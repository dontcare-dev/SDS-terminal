package com.silverdev.sdsterminal

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.GZIPInputStream

class TerminalSession(
    val id: String,
    private val context: Context,
    private val rootfsDir: File,
    private val homeDir: File,
    initialRows: Int = 24,
    initialCols: Int = 80
) {
    val buffer = TermBuffer(initialRows, initialCols)
    private val parser = AnsiParser(buffer)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var masterFd: Int = -1
    @Volatile private var readJob: Job? = null
    var isRunning: Boolean = false
        private set

    fun start(forceAndroidShell: Boolean = false) {
        if (masterFd >= 0 || isRunning) return
        isRunning = true
        readJob = scope.launch {
            val bashFile = File(rootfsDir, "bin/bash")
            val isRootfsReady = File(rootfsDir, ".installed_v3").exists() && bashFile.exists()
            
            if (!isRootfsReady && !forceAndroidShell) {
                feed("\r\n[*] Installing Linux environment (Clean)...\r\n")
                rootfsDir.deleteRecursively()
                File(rootfsDir, ".installed_v2").delete()
                File(rootfsDir, ".installed").delete()
                
                try {
                    downloadAndExtract { progress ->
                        val percent = (progress * 100).toInt()
                        feed("\rProgress: $percent%")
                    }
                    
                    if (bashFile.exists()) {
                        feed("\r\n[*] Extraction verified. Starting bash...\r\n\r\n")
                        File(rootfsDir, ".installed_v3").writeText("ok")
                    } else {
                        feed("\r\n[!] Extraction failed: /bin/bash missing.\r\n")
                        forceAndroidShell
                    }
                } catch (e: Exception) {
                    feed("\r\n[!] Download failed: ${e.message}\r\n[!] Falling back to Android shell.\r\n\r\n")
                    forceAndroidShell
                }
            }

            val useShell = forceAndroidShell || !File(rootfsDir, ".installed_v3").exists()
            masterFd = if (useShell) launchAndroidShell() else launchProot()

            if (masterFd < 0) {
                feed("\r\n[Failed to start PTY]\r\n")
                isRunning = false
                return@launch
            }
            readLoop()
        }
    }

    private fun downloadAndExtract(onProgress: (Float) -> Unit) {
        val url = "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04-base-arm64.tar.gz"
        val archive = File(context.cacheDir, "ubuntu.tar.gz")
        
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 30_000; readTimeout = 60_000 }
        conn.inputStream.use { input ->
            FileOutputStream(archive).use { out ->
                val buf = ByteArray(64 * 1024); var total = 0L; val size = conn.contentLengthLong
                var lastUpdate = 0L
                while (true) {
                    val n = input.read(buf); if (n <= 0) break
                    out.write(buf, 0, n); total += n
                    if (size > 0) {
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            onProgress(total.toFloat() / size)
                            lastUpdate = now
                        }
                    }
                }
            }
        }
        
        rootfsDir.mkdirs()
        GZIPInputStream(archive.inputStream()).use { gz ->
            TarArchiveInputStream(gz).use { tar ->
                var e = tar.nextEntry
                while (e != null) {
                    val out = File(rootfsDir, e.name)
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos -> tar.copyTo(fos) }
                        
                        val mode = e.mode
                        if ((mode and 0b001_000_000) != 0) out.setExecutable(true, false)
                        if ((mode and 0b000_100_000) != 0) out.setReadable(true, false)
                        if ((mode and 0b000_010_000) != 0) out.setWritable(true, false)
                    }
                    e = tar.nextEntry
                }
            }
        }
        archive.delete()
    }

    private fun launchProot(): Int {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        homeDir.mkdirs()
        
        val tmpDir = File(context.filesDir, "tmp")
        tmpDir.mkdirs()

        val hasSdCard = Environment.isExternalStorageManager()
        val bindSdCard = if (hasSdCard) arrayOf("-b", "/storage/emulated/0:/sdcard") else arrayOf()

        val args = mutableListOf(
            "proot",
            "-0",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${homeDir.absolutePath}:/root", "-w", "/root", "--link2symlink",
            "-b", "${context.applicationInfo.nativeLibraryDir}:/data/data/com.termux/files/usr/libexec",
            "/bin/bash", "--login"
        )
        args.addAll(bindSdCard)

        val env = arrayOf(
            "PROOT_LOADER=$nativeDir/libproot-loader.so",
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )

        val fd = PtyNative.openPty("$nativeDir/libproot.so", args.toTypedArray(), env)
        if (fd >= 0) PtyNative.setWindowSize(fd, buffer.rows, buffer.cols)
        return fd
    }

    private fun launchAndroidShell(): Int {
        val fd = PtyNative.openPty(
            "/system/bin/sh", arrayOf("-i"),
            arrayOf("PATH=/sbin:/system/sbin:/system/bin:/system/xbin", "TERM=xterm-256color", "HOME=/data/local/tmp")
        )
        if (fd >= 0) PtyNative.setWindowSize(fd, buffer.rows, buffer.cols)
        return fd
    }

    private suspend fun readLoop() {
        val buf = ByteArray(8192)
        while (masterFd >= 0) {
            val n = PtyNative.read(masterFd, buf)
            if (n > 0) {
                parser.feed(buf, n)
                buffer.bump()
                delay(16) 
            } else if (n < 0) {
                break
            } else {
                delay(16)
            }
        }
        isRunning = false
        feed("\r\n[Session ended]\r\n")
    }

    fun write(data: ByteArray) {
        val fd = masterFd; if (fd < 0) return
        var off = 0
        while (off < data.size) {
            val chunk = data.copyOfRange(off, data.size)
            val n = PtyNative.write(fd, chunk, chunk.size)
            if (n <= 0) break
            off += n
        }
    }

    fun resize(rows: Int, cols: Int) {
        if (rows <= 0 || cols <= 0) return
        if (rows == buffer.rows && cols == buffer.cols) return
        buffer.resize(rows, cols)
        if (masterFd >= 0) PtyNative.setWindowSize(masterFd, rows, cols)
    }

    fun close() {
        readJob?.cancel()
        if (masterFd >= 0) PtyNative.close(masterFd)
        masterFd = -1; isRunning = false; scope.cancel()
    }

    private fun feed(s: String) { val b = s.toByteArray(); parser.feed(b, b.size); buffer.bump() }
}

class SessionManager(private val context: Context) {
    private val sessions = mutableListOf<TerminalSession>()
    private var activeIndex = 0

    fun newSession(forceAndroidShell: Boolean = false): TerminalSession {
        val s = TerminalSession(
            id = UUID.randomUUID().toString(),
            context = context,
            rootfsDir = File(context.filesDir, "ubuntu"),
            homeDir = File(context.filesDir, "home")
        )
        sessions.add(s); activeIndex = sessions.size - 1
        s.start(forceAndroidShell)
        return s
    }

    fun getSessions() = sessions.toList()
    fun active() = sessions.getOrNull(activeIndex)
    fun setActive(i: Int) { if (i in sessions.indices) activeIndex = i }
    fun closeSession(i: Int) {
        if (i !in sessions.indices) return
        sessions.removeAt(i).close()
        if (activeIndex >= sessions.size) activeIndex = (sessions.size - 1).coerceAtLeast(0)
    }
}