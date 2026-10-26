package com.silverdev.sdsterminal

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationCompat
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
            .setContentText("Terminal session running")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SdsApp
        
        val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        val storageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

        setContent {
            SdsTheme {
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                            storageLauncher.launch(intent)
                        } catch (e: Exception) {
                            storageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                }
                
                Box(Modifier.fillMaxSize().background(Bg)) {
                    RootNav(app.sessionManager, app.rootfsDir)
                }
            }
        }
    }
}