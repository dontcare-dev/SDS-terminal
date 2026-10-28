package com.silverdev.sdsterminal

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

class GameBoosterService : Service() {
    private var job: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPkg = intent?.getStringExtra("target_pkg") ?: ""
        val ignoredPkgs = intent?.getStringArrayListExtra("ignored_pkgs") ?: arrayListOf()
        val networkPriority = intent?.getBooleanExtra("network_priority", false) ?: false

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(NotificationChannel("sds_booster", "Game Booster", NotificationManager.IMPORTANCE_LOW))
        }

        val notification = NotificationCompat.Builder(this, "sds_booster")
            .setContentTitle("Game Booster Active")
            .setContentText("Optimizing RAM and Network...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        
        startForeground(42, notification)

        if (networkPriority) {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "SDS:GameBoosterWifi")
                } else {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SDS:GameBoosterWifi")
                }
                wifiLock?.acquire()

                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SDS:GameBoosterWake")
                wakeLock?.acquire(60 * 60 * 1000L)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        job = scope.launch {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = packageManager
            while (isActive) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                if (mi.availMem < (mi.totalMem * 0.20)) {
                    val apps = pm.getInstalledApplications(0)
                    for (app in apps) {
                        if (app.packageName != targetPkg && app.packageName != packageName && !ignoredPkgs.contains(app.packageName)) {
                            am.killBackgroundProcesses(app.packageName)
                        }
                    }
                }
                delay(10000)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SdsApp
        val prefs = getSharedPreferences("sds_prefs", Context.MODE_PRIVATE)
        
        setContent {
            var bgImageUri by remember { mutableStateOf<String?>(prefs.getString("bg_image", null)) }
            var hasStoragePermission by remember { mutableStateOf(Environment.isExternalStorageManager()) }

            val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                hasStoragePermission = Environment.isExternalStorageManager()
            }

            val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {}
                    bgImageUri = uri.toString()
                    prefs.edit().putString("bg_image", bgImageUri).apply()
                }
            }

            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }

            SdsTheme(bgImageUri = bgImageUri) {
                RootNav(
                    manager = app.sessionManager,
                    rootDir = app.rootfsDir,
                    bgImageUri = bgImageUri,
                    onPickImage = {
                        if (!hasStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.addCategory("android.intent.category.DEFAULT")
                                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                                storageLauncher.launch(intent)
                            } catch (e: Exception) {
                                storageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        } else {
                            imagePicker.launch("image/*")
                        }
                    }
                )
            }
        }
    }
}