package com.silverdev.sdsterminal

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

val Bg = Color(0xFF050505)
val Surface = Color(0xFF0F0F11)
val Border = Color(0xFF1B1B1E)
val Border2 = Color(0xFF2B2B30)
val TextPrimary = Color(0xFFEDEDF0)
val TextSecondary = Color(0xFF9B9BA3)
val TextTertiary = Color(0xFF6A6A73)
val Green = Color(0xFF3ECF8E)
val Red = Color(0xFFF2555A)
val Accent = Color(0xFF6CB4EE)
val SansFamily = FontFamily.SansSerif
val MonoFamily = FontFamily.Monospace
val TerminalBlack = Color(0xFF0D0D0D)
val TerminalExtraRow = Color(0xFF101114)
val ImageUrl = "https://i.ibb.co/Z6cSZgYB/favicon.png"

private val DarkColors = darkColorScheme(
    primary = TextPrimary, onPrimary = Bg, secondary = Green,
    background = Color.Transparent, onBackground = TextPrimary,
    surface = Surface.copy(alpha = 0.85f), onSurface = TextPrimary,
    surfaceVariant = Surface.copy(alpha = 0.85f), onSurfaceVariant = TextSecondary,
    outline = Border, error = Red
)

@Composable
fun SdsTheme(bgImageUri: String? = null, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            if (bgImageUri != null) {
                AsyncImage(
                    model = bgImageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            }
            content()
        }
    }
}

@Composable
fun TerminalScreen(manager: SessionManager) {
    var sessions by remember { mutableStateOf(manager.getSessions()) }
    var activeIndex by remember { mutableStateOf(sessions.size - 1) }
    fun refresh() {
        sessions = manager.getSessions()
        if (activeIndex >= sessions.size) activeIndex = (sessions.size - 1).coerceAtLeast(0)
    }
    LaunchedEffect(Unit) {
        if (manager.getSessions().isEmpty()) {
            manager.newSession(); sessions = manager.getSessions(); activeIndex = sessions.size - 1
        }
    }
    Column(Modifier.fillMaxSize().background(TerminalBlack).imePadding()) {
        if (sessions.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = activeIndex, containerColor = TerminalExtraRow,
                contentColor = TextPrimary, edgePadding = 0.dp, modifier = Modifier.fillMaxWidth(),
                divider = { HorizontalDivider(color = Border, thickness = 1.dp) }
            ) {
                sessions.forEachIndexed { i, _ ->
                    Tab(
                        selected = i == activeIndex,
                        onClick = { activeIndex = i; manager.setActive(i) },
                        text = { Text("bash", fontFamily = MonoFamily, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) },
                        icon = {
                            if (sessions.size > 1) IconButton(
                                onClick = { manager.closeSession(i); refresh() },
                                modifier = Modifier.size(24.dp)
                            ) { Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp)) }
                        }
                    )
                }
            }
        }
        val active = sessions.getOrNull(activeIndex)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (active != null) {
                TerminalRenderer(
                    buffer = active.buffer,
                    onInput = { active.write(it) },
                    onResize = { r, c -> active.resize(r, c) },
                    onKeyEvent = { ev -> handleKey(ev, active) },
                    fontSize = 14.sp
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Starting shell…", color = Color.Gray, fontFamily = MonoFamily)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(TerminalExtraRow).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                manager.newSession(); refresh(); activeIndex = manager.getSessions().size - 1
            }) { Icon(Icons.Default.Add, contentDescription = "New tab", tint = TextSecondary) }
        }
        ExtraKeysRow(onSend = { active?.write(it.toByteArray()) }, modifier = Modifier.fillMaxWidth())
    }
}

private fun handleKey(event: KeyEvent, session: TerminalSession): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    when (event.key) {
        Key.Backspace -> { session.write(byteArrayOf(0x7F)); return true }
        Key.Enter -> { session.write(byteArrayOf(0x0D)); return true }
        Key.Escape -> { session.write(byteArrayOf(0x1B)); return true }
        Key.Tab -> { session.write(byteArrayOf(0x09)); return true }
        Key.DirectionUp -> { session.write("\u001B[A".toByteArray()); return true }
        Key.DirectionDown -> { session.write("\u001B[B".toByteArray()); return true }
        Key.DirectionRight -> { session.write("\u001B[C".toByteArray()); return true }
        Key.DirectionLeft -> { session.write("\u001B[D".toByteArray()); return true }
        else -> {}
    }
    val cp = event.nativeKeyEvent.unicodeChar
    if (event.isCtrlPressed && cp in 'a'.code..'z'.code) { session.write(byteArrayOf((cp - 'a'.code + 1).toByte())); return true }
    if (event.isAltPressed && cp != 0) { session.write(byteArrayOf(0x1B, cp.toByte())); return true }
    return false
}

@Composable
fun HomeScreen(bgImageUri: String?, onPickImage: () -> Unit, onTerminal: () -> Unit, onFiles: () -> Unit, onAi: () -> Unit, onOptimize: () -> Unit, onSecurity: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageUrl,
                contentDescription = "Logo",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Border2, RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("SDS Terminal", fontSize = 24.sp, fontFamily = SansFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("v1.0.0", fontSize = 13.sp, fontFamily = MonoFamily, color = TextTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }

        Spacer(Modifier.height(40.dp))

        if (bgImageUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPickImage() },
                colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Accent)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Change Background", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Tap to select an image from your gallery", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text("QUICK ACTIONS", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(16.dp))

        ActionCard("Terminal", "Launch bash shell", Icons.Default.Terminal, onTerminal)
        Spacer(Modifier.height(12.dp))
        ActionCard("AI Assistant", "Ask questions or open apps", Icons.Default.AutoAwesome, onAi)
        Spacer(Modifier.height(12.dp))
        ActionCard("File Browser", "Browse Android storage", Icons.Default.Folder, onFiles)
        Spacer(Modifier.height(12.dp))
        ActionCard("Game Booster", "Optimize RAM & performance", Icons.Default.Bolt, onOptimize)
        Spacer(Modifier.height(12.dp))
        ActionCard("Settings", "Permissions & Appearance", Icons.Default.Settings, onSecurity)
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Bg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}

@Composable
fun FileBrowserScreen(rootDir: File) {
    var currentDir by remember { mutableStateOf(rootDir) }
    var entries by remember { mutableStateOf(emptyList<File>()) }
    LaunchedEffect(currentDir) {
        entries = withContext(Dispatchers.IO) {
            currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }, enabled = currentDir != rootDir) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = TextPrimary)
            }
            Text(currentDir.name.ifEmpty { "/" }, color = TextPrimary, fontSize = 20.sp, fontFamily = SansFamily, fontWeight = FontWeight.Bold)
        }
        Text(currentDir.absolutePath, color = TextTertiary, fontSize = 12.sp, fontFamily = MonoFamily, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        
        entries.forEach { file ->
            Row(
                Modifier.fillMaxWidth().clickable { if (file.isDirectory) currentDir = file }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    null, tint = if (file.isDirectory) TextPrimary else TextTertiary, modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(file.name, color = TextPrimary, fontSize = 15.sp, fontFamily = SansFamily)
            }
            HorizontalDivider(color = Border)
        }
    }
}

@Composable
fun OptimizationScreen() {
    val context = LocalContext.current
    val pm = context.packageManager
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    var allApps by remember { mutableStateOf(listOf<ApplicationInfo>()) }
    var selectedGame by remember { mutableStateOf<ApplicationInfo?>(null) }
    var ignoredApps by remember { mutableStateOf(setOf<String>()) }
    var showIgnoreList by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf("") }
    var realTimeBoost by remember { mutableStateOf(true) }
    
    var totalRam by remember { mutableStateOf(0L) }
    var usedRam by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
            allApps = apps
        }
        
        while (true) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            totalRam = mi.totalMem / (1024 * 1024)
            usedRam = (mi.totalMem - mi.availMem) / (1024 * 1024)
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Game Booster", fontFamily = SansFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        
        Card(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("System RAM", color = TextSecondary, fontSize = 14.sp)
                    Text("$usedRam / $totalRam MB", color = TextPrimary, fontFamily = MonoFamily, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                val progress = if (totalRam > 0) usedRam.toFloat() / totalRam.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (progress > 0.8f) Red else Green,
                    trackColor = Border
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Select Game to Boost", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        
        Card(
            Modifier.fillMaxWidth().heightIn(max = 200.dp),
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            LazyColumn {
                items(allApps) { app ->
                    val isSelected = selectedGame?.packageName == app.packageName
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedGame = app }
                            .background(if (isSelected) Surface else Color.Transparent)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            pm.getApplicationIcon(app),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            app.loadLabel(pm).toString(),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Green)
                    }
                    HorizontalDivider(color = Border)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Real-Time Auto-Boost", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Clears RAM every 10s while playing", color = TextSecondary, fontSize = 13.sp)
            }
            Switch(
                checked = realTimeBoost,
                onCheckedChange = { realTimeBoost = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Green)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { showIgnoreList = !showIgnoreList },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface.copy(alpha = 0.85f), contentColor = TextPrimary),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Border)
            ) {
                Text("Ignore List (${ignoredApps.size})", fontSize = 14.sp)
            }
            
            Button(
                onClick = {
                    if (selectedGame == null) {
                        toastMsg = "Select a game first!"
                    } else {
                        val targetPkg = selectedGame!!.packageName
                        var killed = 0
                        for (app in allApps) {
                            if (app.packageName != targetPkg && app.packageName != context.packageName && !ignoredApps.contains(app.packageName)) {
                                am.killBackgroundProcesses(app.packageName)
                                killed++
                            }
                        }
                        
                        if (realTimeBoost) {
                            val intent = Intent(context, GameBoosterService::class.java).apply {
                                putExtra("target_pkg", targetPkg)
                                putStringArrayListExtra("ignored_pkgs", ArrayList(ignoredApps))
                            }
                            ContextCompat.startForegroundService(context, intent)
                        }

                        toastMsg = "Boosted! Cleared $killed apps. Launching ${selectedGame!!.loadLabel(pm)}..."
                        val intent = pm.getLaunchIntentForPackage(targetPkg)
                        if (intent != null) context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Boost & Launch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        if (toastMsg.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(toastMsg, color = Green, fontSize = 13.sp, fontFamily = MonoFamily, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showIgnoreList) {
        AlertDialog(
            onDismissRequest = { showIgnoreList = false },
            title = { Text("Ignore List", color = TextPrimary) },
            text = {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(allApps) { app ->
                        val isIgnored = ignoredApps.contains(app.packageName)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                ignoredApps = if (isIgnored) ignoredApps - app.packageName else ignoredApps + app.packageName
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isIgnored,
                                onCheckedChange = { 
                                    ignoredApps = if (it) ignoredApps + app.packageName else ignoredApps - app.packageName 
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Green)
                            )
                            Text(app.loadLabel(pm).toString(), color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIgnoreList = false }) {
                    Text("Done", color = Green)
                }
            },
            containerColor = Surface
        )
    }
}

data class ChatMessage(val isUser: Boolean, val text: String)

@Composable
fun AiScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("sds_prefs", Context.MODE_PRIVATE)
    val apiKey = prefs.getString("gemini_key", "") ?: ""
    
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMessage(false, "Hello. I am the SDS Assistant. I can answer questions or open apps for you. Try: 'open discord'"))) }
    var isTyping by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val client = remember { OkHttpClient() }

    fun sendMessage() {
        if (inputText.isBlank()) return
        val userMsg = inputText
        messages = messages + ChatMessage(true, userMsg)
        inputText = ""
        
        val lower = userMsg.lowercase()
        if (lower.startsWith("open ")) {
            val appName = lower.removePrefix("open ").trim()
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            val target = packages.find { 
                (it.loadLabel(pm).toString().lowercase().contains(appName)) || 
                (it.packageName.lowercase().contains(appName))
            }
            
            if (target != null) {
                val intent = pm.getLaunchIntentForPackage(target.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                    messages = messages + ChatMessage(false, "Opened ${target.loadLabel(pm)}.")
                    return
                }
            } else {
                messages = messages + ChatMessage(false, "App '$appName' not found on this device.")
                return
            }
        }

        isTyping = true
        scope.launch {
            val reply = callGemini(client, apiKey, userMsg)
            messages = messages + ChatMessage(false, reply)
            isTyping = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("AI Assistant", fontFamily = SansFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        }
        
        if (apiKey.isEmpty()) {
            Text("Please set your Gemini API Key in Settings to use the AI.", color = Red, fontFamily = MonoFamily, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(messages) { msg ->
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Text(
                        msg.text,
                        fontFamily = MonoFamily,
                        fontSize = 14.sp,
                        color = if (msg.isUser) Bg else TextPrimary,
                        modifier = Modifier
                            .background(if (msg.isUser) TextPrimary else Surface.copy(alpha = 0.85f))
                            .border(1.dp, if (msg.isUser) Color.Transparent else Border, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            }
            if (isTyping) {
                item {
                    Text("Thinking...", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(8.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, color = TextPrimary),
                cursorBrush = SolidColor(TextPrimary),
                modifier = Modifier.weight(1f).background(Surface.copy(alpha = 0.85f)).border(1.dp, Border, RoundedCornerShape(8.dp)).padding(12.dp),
                decorationBox = { inner ->
                    if (inputText.isEmpty()) Text("Ask anything or 'open youtube'...", fontFamily = MonoFamily, fontSize = 14.sp, color = TextTertiary)
                    inner()
                }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { sendMessage() },
                modifier = Modifier.background(TextPrimary).size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Bg)
            }
        }
    }
}

suspend fun callGemini(client: OkHttpClient, apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
    if (apiKey.isEmpty()) return@withContext "Please set your Gemini API Key in Settings."
    
    try {
        val json = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().apply {
                put("parts", org.json.JSONArray().put(JSONObject().apply {
                    put("text", "You are the AI assistant inside SDS Terminal, a Linux terminal app. Keep responses short and helpful. User says: $prompt")
                }))
            }))
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val resBody = response.body?.string() ?: return@withContext "Error: Empty response"
            val jsonRes = JSONObject(resBody)
            if (jsonRes.has("error")) {
                return@withContext "API Error: ${jsonRes.getJSONObject("error").getString("message")}"
            }
            val text = jsonRes.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            return@withContext text
        }
    } catch (e: Exception) {
        return@withContext "Network Error: ${e.message}"
    }
}

@Composable
fun SecurityScreen(bgImageUri: String?, onPickImage: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("sds_prefs", Context.MODE_PRIVATE)
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", fontFamily = SansFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        
        Spacer(Modifier.height(32.dp))
        
        Text("APPEARANCE", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onPickImage() },
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text("Change Background Image", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(if (bgImageUri != null) "SET" else "NONE", fontFamily = MonoFamily, fontSize = 12.sp, color = Accent)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text("AI CONFIGURATION", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Gemini API Key", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = apiKey,
                    onValueChange = { 
                        apiKey = it
                        prefs.edit().putString("gemini_key", it).apply()
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, color = TextPrimary),
                    cursorBrush = SolidColor(TextPrimary),
                    modifier = Modifier.fillMaxWidth().background(Bg).border(1.dp, Border).padding(12.dp),
                    decorationBox = { inner ->
                        if (apiKey.isEmpty()) Text("Enter API Key here...", fontFamily = MonoFamily, fontSize = 14.sp, color = TextTertiary)
                        inner()
                    }
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Text("STORAGE", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("All Files Access", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "Required to access /sdcard inside the Linux environment. Grant this permission in Android settings.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun RootNav(manager: SessionManager, rootDir: File, bgImageUri: String?, onPickImage: () -> Unit) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route ?: "home"
    
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                HomeScreen(
                    bgImageUri = bgImageUri,
                    onPickImage = onPickImage,
                    onTerminal = { nav.navigate("terminal") },
                    onFiles = { nav.navigate("files") },
                    onAi = { nav.navigate("ai") },
                    onOptimize = { nav.navigate("optimize") },
                    onSecurity = { nav.navigate("security") }
                )
            }
            composable("terminal") { TerminalScreen(manager) }
            composable("ai") { AiScreen() }
            composable("optimize") { OptimizationScreen() }
            composable("files") { FileBrowserScreen(rootDir) }
            composable("security") { SecurityScreen(bgImageUri, onPickImage) }
        }
        
        if (current != "home" && current != "terminal") {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Surface.copy(alpha = 0.85f)).border(1.dp, Border, CircleShape)
                        .clickable { nav.navigate("home") { launchSingleTop = true } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
        }
    }
}