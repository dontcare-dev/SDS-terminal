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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import java.io.File

val Bg = Color(0xFF050505)
val Surface = Color(0xFF0A0A0B)
val Surface2 = Color(0xFF0F0F11)
val Border = Color(0xFF1B1B1E)
val Border2 = Color(0xFF2B2B30)
val TextPrimary = Color(0xFFEDEDF0)
val TextSecondary = Color(0xFF9B9BA3)
val TextTertiary = Color(0xFF6A6A73)
val Green = Color(0xFF3ECF8E)
val Red = Color(0xFFF2555A)
val Accent = Color(0xFF6CB4EE) // Blue accent for links
val MonoFamily = FontFamily.Monospace
val TerminalBlack = Color(0xFF0D0D0D)
val TerminalExtraRow = Color(0xFF101114)
val ImageUrl = "https://i.ibb.co/Z6cSZgYB/favicon.png"

private val DarkColors = darkColorScheme(
    primary = TextPrimary, onPrimary = Bg, secondary = Green,
    background = Bg, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = Surface2, onSurfaceVariant = TextSecondary,
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
                        text = { Text("bash", fontFamily = MonoFamily, fontSize = 12.sp) },
                        icon = {
                            if (sessions.size > 1) IconButton(
                                onClick = { manager.closeSession(i); refresh() },
                                modifier = Modifier.size(20.dp)
                            ) { Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp)) }
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
                    fontSize = 13.sp
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Starting shell…", color = Color.Gray, fontFamily = MonoFamily)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(TerminalExtraRow).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun HomeScreen(bgImageUri: String?, onPickImage: () -> Unit, onTerminal: () -> Unit, onFiles: () -> Unit, onPackages: () -> Unit, onSecurity: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        // Left Sidebar (File Tree)
        Column(
            Modifier.width(130.dp).fillMaxHeight().background(Bg.copy(alpha = 0.9f))
                .border(1.dp, Border).padding(top = 16.dp)
        ) {
            Text("v SDS", fontFamily = MonoFamily, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            
            FileTreeItem("about.md", isSelected = true)
            FileTreeItem("terminal.sh", onClick = onTerminal)
            FileTreeItem("files/", onClick = onFiles)
            FileTreeItem("packages/", onClick = onPackages)
            FileTreeItem("settings.md", onClick = onSecurity)
            
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Text("status: ok", fontFamily = MonoFamily, fontSize = 10.sp, color = Green, modifier = Modifier.padding(12.dp))
            Text("ram: ~120mb", fontFamily = MonoFamily, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            Spacer(Modifier.height(16.dp))
        }

        // Right Content (Markdown)
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
        ) {
            // Top tab bar
            Row(Modifier.fillMaxWidth().height(36.dp).background(Bg.copy(alpha = 0.9f)).border(1.dp, Border), verticalAlignment = Alignment.CenterVertically) {
                Text("  about.md", fontFamily = MonoFamily, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.padding(horizontal = 12.dp))
            }

            Column(Modifier.padding(20.dp)) {
                Text("# about", fontFamily = MonoFamily, fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                Text(
                    "I'm SDS Terminal. A native Android terminal built with Kotlin and Jetpack Compose.",
                    fontFamily = MonoFamily, fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp
                )
                
                Spacer(Modifier.height(24.dp))
                Text("## quick facts", fontFamily = MonoFamily, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                MarkdownBullet("Runs Ubuntu Linux via proot")
                MarkdownBullet("No root required")
                MarkdownBullet("Fully offline capable")
                
                Spacer(Modifier.height(24.dp))
                
                // The Big Logo
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = ImageUrl,
                        contentDescription = "Logo",
                        modifier = Modifier.size(180.dp)
                    )
                }
                
                Spacer(Modifier.height(32.dp))
                
                // Background prompt
                if (bgImageUri == null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Surface)
                            .border(1.dp, Border2, RoundedCornerShape(4.dp)).clickable { onPickImage() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[!]", fontFamily = MonoFamily, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text("No background set. Click here to pick an image.", fontFamily = MonoFamily, fontSize = 12.sp, color = TextPrimary)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Surface)
                            .border(1.dp, Border2, RoundedCornerShape(4.dp)).clickable { onPickImage() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[✓]", fontFamily = MonoFamily, fontSize = 14.sp, color = Green, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text("Background image loaded. Click to change.", fontFamily = MonoFamily, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Navigation buttons mimicking links
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarkdownLink("← prev")
                    MarkdownLink("random")
                    MarkdownLink("next →")
                }
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun FileTreeItem(name: String, isSelected: Boolean = false, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .background(if (isSelected) Surface else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("├─ ", fontFamily = MonoFamily, fontSize = 12.sp, color = Border2)
        Text(name, fontFamily = MonoFamily, fontSize = 12.sp, color = if (isSelected) TextPrimary else TextSecondary)
    }
}

@Composable
fun MarkdownBullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("- ", fontFamily = MonoFamily, fontSize = 13.sp, color = TextTertiary)
        Text(text, fontFamily = MonoFamily, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
fun MarkdownLink(text: String) {
    Text(
        text, fontFamily = MonoFamily, fontSize = 12.sp, color = TextSecondary,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Border2, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
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
        Row(Modifier.fillMaxWidth().border(1.dp, Border).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }, enabled = currentDir != rootDir) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = TextPrimary)
            }
            Text(currentDir.name.ifEmpty { "/" }, color = TextPrimary, fontSize = 14.sp, fontFamily = MonoFamily)
        }
        Text(currentDir.absolutePath, color = TextTertiary, fontSize = 11.sp, fontFamily = MonoFamily, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        
        entries.forEach { file ->
            Row(
                Modifier.fillMaxWidth().clickable { if (file.isDirectory) currentDir = file }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (file.isDirectory) "d " else "- ", fontFamily = MonoFamily, fontSize = 13.sp, color = TextTertiary)
                Text(file.name, color = TextPrimary, fontSize = 13.sp, fontFamily = MonoFamily)
            }
            HorizontalDivider(color = Border)
        }
    }
}

@Composable
fun SecurityScreen(bgImageUri: String?, onPickImage: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("# settings.md", fontFamily = MonoFamily, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Surface)
                .border(1.dp, Border2, RoundedCornerShape(4.dp)).clickable { onPickImage() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("bg_image", fontFamily = MonoFamily, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(if (bgImageUri != null) "[set]" else "[not set]", fontFamily = MonoFamily, fontSize = 12.sp, color = Accent)
        }
        
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Surface)
                .border(1.dp, Border2, RoundedCornerShape(4.dp)).padding(16.dp)
        ) {
            Text("storage_access", fontFamily = MonoFamily, fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("To access /sdcard inside Linux, grant 'All Files Access' when prompted.", fontSize = 12.sp, fontFamily = MonoFamily, color = TextSecondary, lineHeight = 18.sp)
        }
    }
}

@Composable
fun PackagesScreen(onInstall: (String) -> Unit) {
    var pkg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f)).imePadding().padding(20.dp)) {
        Text("# packages/", fontFamily = MonoFamily, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Install apt packages inside Ubuntu.", fontSize = 12.sp, fontFamily = MonoFamily, color = TextSecondary)
        
        Spacer(Modifier.height(24.dp))
        BasicTextField(
            value = pkg,
            onValueChange = { pkg = it },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, color = TextPrimary),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth().background(Surface).border(1.dp, Border2).padding(12.dp),
            decorationBox = { inner ->
                if (pkg.isEmpty()) Text("package_name...", fontFamily = MonoFamily, fontSize = 14.sp, color = TextTertiary)
                inner()
            }
        )
        
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().background(TextPrimary).clickable { if (pkg.isNotBlank()) onInstall(pkg.trim()) }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("RUN apt install", fontFamily = MonoFamily, fontSize = 13.sp, color = Bg, fontWeight = FontWeight.Bold)
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
        
        if (current != "home" && current != "terminal") {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Surface).border(1.dp, Border2, CircleShape)
                        .clickable { nav.navigate("home") { launchSingleTop = true } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
        }
    }
}