package com.silverdev.sdsterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import coil.compose.AsyncImage
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
    background = Bg, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)))
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
    val cp = event.nativeKeyCode
    if (event.isCtrlPressed && cp in 'a'.code..'z'.code) { session.write(byteArrayOf((cp - 'a'.code + 1).toByte())); return true }
    if (event.isAltPressed && cp != 0) { session.write(byteArrayOf(0x1B, cp.toByte())); return true }
    return false
}

@Composable
fun HomeScreen(bgImageUri: String?, onPickImage: () -> Unit, onTerminal: () -> Unit, onFiles: () -> Unit, onPackages: () -> Unit, onSecurity: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        // Header
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

        // Background Prompt (Massive and obvious)
        if (bgImageUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPickImage() },
                colors = CardDefaults.cardColors(containerColor = Surface),
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

        // Main Actions
        Text("QUICK ACTIONS", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(16.dp))

        ActionCard("Terminal", "Launch bash shell", Icons.Default.Terminal, onTerminal)
        Spacer(Modifier.height(12.dp))
        ActionCard("File Browser", "Browse Android storage", Icons.Default.Folder, onFiles)
        Spacer(Modifier.height(12.dp))
        ActionCard("Packages", "Install apt packages", Icons.Default.Memory, onPackages)
        Spacer(Modifier.height(12.dp))
        ActionCard("Settings", "Permissions & Appearance", Icons.Default.Settings, onSecurity)
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.6f)),
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
        entries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }
    }
    Column(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f))) {
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
fun SecurityScreen(bgImageUri: String?, onPickImage: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", fontFamily = SansFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        
        Spacer(Modifier.height(32.dp))
        
        Text("APPEARANCE", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onPickImage() },
            colors = CardDefaults.cardColors(containerColor = Surface),
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
        
        Text("STORAGE", fontFamily = MonoFamily, fontSize = 12.sp, color = TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
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
fun PackagesScreen(onInstall: (String) -> Unit) {
    var pkg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f)).imePadding().padding(20.dp)) {
        Text("Packages", fontFamily = SansFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Install apt packages inside Ubuntu.", fontSize = 14.sp, color = TextSecondary)
        
        Spacer(Modifier.height(32.dp))
        
        BasicTextField(
            value = pkg,
            onValueChange = { pkg = it },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 16.sp, color = TextPrimary),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth().background(Surface).border(1.dp, Border).padding(16.dp),
            decorationBox = { inner ->
                if (pkg.isEmpty()) Text("package_name (e.g. python3)", fontFamily = MonoFamily, fontSize = 16.sp, color = TextTertiary)
                inner()
            }
        )
        
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().background(TextPrimary).clip(RoundedCornerShape(8.dp)).clickable { if (pkg.isNotBlank()) onInstall(pkg.trim()) }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("INSTALL", fontFamily = SansFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Bg)
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
                    onPackages = { nav.navigate("packages") },
                    onSecurity = { nav.navigate("security") }
                )
            }
            composable("terminal") { TerminalScreen(manager) }
            composable("files") { FileBrowserScreen(rootDir) }
            composable("packages") {
                PackagesScreen { p ->
                    val s = manager.active() ?: manager.newSession()
                    s.write("apt update && apt install -y $p\n".toByteArray())
                    nav.navigate("terminal")
                }
            }
            composable("security") { SecurityScreen(bgImageUri, onPickImage) }
        }
        
        // Floating back button for sub-screens
        if (current != "home" && current != "terminal") {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Surface).border(1.dp, Border, CircleShape)
                        .clickable { nav.navigate("home") { launchSingleTop = true } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
        }
    }
}