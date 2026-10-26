package com.silverdev.sdsterminal

import android.content.Context
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
import java.security.MessageDigest
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

    fun start(useAndroidShell: Boolean = false) {
        if (masterFd >= 0) return
        masterFd = if (useAndroidShell) launchAndroidShell() else launchProot()
        if (masterFd < 0) { feed("\r\n[Failed to start session]\r\n"); return }
        isRunning = true
        readJob = scope.launch { readLoop() }
    }

    private fun launchProot(): Int {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        homeDir.mkdirs()
        val args = arrayOf(
            "-r", rootfsDir.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${homeDir.absolutePath}:/root", "-w", "/root", "--link2symlink",
            "/usr/bin/env", "-i", "HOME=/root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/bash", "--login"
        )
        val fd = PtyNative.openPty("$nativeDir/libproot.so", args, arrayOf())
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
            if (n > 0) { parser.feed(buf, n); buffer.bump() }
            else if (n < 0) break
            else delay(16)
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

    fun prootAvailable() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").exists()

    fun newSession(forceAndroidShell: Boolean = false): TerminalSession {
        val useShell = forceAndroidShell || !prootAvailable()
        val s = TerminalSession(
            id = UUID.randomUUID().toString(),
            context = context,
            rootfsDir = File(context.filesDir, "ubuntu"),
            homeDir = File(context.filesDir, "home")
        )
        sessions.add(s); activeIndex = sessions.size - 1
        s.start(useShell)
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

class RootfsInstaller(private val context: Context) {
    private val url = "https://REPLACE_WITH_REAL_ROOTFS_URL/ubuntu-minimal.tar.gz"
    private val sha = "REPLACE_WITH_SHA256_HEX"

    suspend fun install(onProgress: (Float) -> Unit): File = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "ubuntu")
        val marker = File(root, ".installed")
        if (marker.exists()) return@withContext root
        root.mkdirs()
        val archive = File(context.cacheDir, "ubuntu.tar.gz")
        download(archive, onProgress)
        val actual = sha256(archive)
        if (!actual.equals(sha, ignoreCase = true)) {
            archive.delete()
            throw IllegalStateException("Rootfs checksum mismatch: $actual")
        }
        extract(archive, root)
        marker.writeText("ok"); archive.delete()
        root
    }

    private fun download(target: File, progress: (Float) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 30_000; readTimeout = 60_000 }
        conn.inputStream.use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024); var total = 0L; val size = conn.contentLengthLong
                while (true) {
                    val n = input.read(buf); if (n <= 0) break
                    out.write(buf, 0, n); total += n
                    if (size > 0) progress(total.toFloat() / size)
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { val buf = ByteArray(64 * 1024); while (true) { val n = it.read(buf); if (n <= 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extract(archive: File, dest: File) {
        GZIPInputStream(archive.inputStream()).use { gz ->
            TarArchiveInputStream(gz).use { tar ->
                var e = tar.nextEntry
                while (e != null) {
                    val out = File(dest, e.name)
                    if (e.isDirectory) out.mkdirs()
                    else { out.parentFile?.mkdirs(); FileOutputStream(out).use { fos -> tar.copyTo(fos) } }
                    e = tar.nextEntry
                }
            }
        }
    }
}