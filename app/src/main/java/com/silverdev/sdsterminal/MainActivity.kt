package com.silverdev.sdsterminal

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File

class SdsApp : Application() {
    lateinit var sessionManager: SessionManager
        private set
    val rootfsDir: File by lazy { File(filesDir, "ubuntu") }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
    }
}

class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(41, buildNotification())
        return START_STICKY
    }
    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(NotificationChannel("sds_sessions", "Sessions", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, "sds_sessions")
            .setContentTitle("SDS Terminal")
            .setContentText("A terminal session is running")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SdsApp
        val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        setContent {
            SdsTheme {
                val prootPresent = File(applicationInfo.nativeLibraryDir, "libproot.so").exists()
                val marker = File(app.rootfsDir, ".installed")
                var rootReady by remember { mutableStateOf(!prootPresent || marker.exists()) }
                var progress by remember { mutableStateOf(0f) }
                var error by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (prootPresent && !marker.exists()) {
                        try {
                            RootfsInstaller(this@MainActivity).install { progress = it }
                            rootReady = true
                        } catch (t: Throwable) {
                            error = t.message ?: "Rootfs install failed"
                        }
                    }
                }
                Box(Modifier.fillMaxSize().background(Bg)) {
                    when {
                        !rootReady && error == null -> InstallingView(progress)
                        else -> RootNav(app.sessionManager, app.rootfsDir)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallingView(progress: Float) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            "Setting up environment… ${(progress * 100).toInt()}%",
            color = TextPrimary, fontFamily = MonoFamily
        )
    }
}