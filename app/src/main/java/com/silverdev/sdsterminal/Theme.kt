package com.silverdev.sdsterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
val Surface = Color(0xFF0A0A0B)
val Surface2 = Color(0xFF0F0F11)
val Border = Color(0xFF1B1B1E)
val Border2 = Color(0xFF2B2B30)
val TextPrimary = Color(0xFFEDEDF0)
val TextSecondary = Color(0xFF9B9BA3)
val TextTertiary = Color(0xFF6A6A73)
val TextQuaternary = Color(0xFF45454D)
val Green = Color(0xFF3ECF8E)
val Red = Color(0xFFF2555A)
val DisplayFamily = FontFamily.Cursive // Fallback for Chewy
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
fun SdsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

@Composable
fun SdsLogo(size: Int = 26, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
            .background(Surface2).border(1.dp, Border2, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageUrl,
            contentDescription = "Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, textColor: Color = TextTertiary) {
    Text(
        text, color = textColor, fontFamily = MonoFamily, fontSize = 9.5.sp,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(Surface)
            .border(1.dp, Border, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

data class NavItem(val label: String, val onClick: () -> Unit)

@Composable
fun SdsNav(title: String, items: List<NavItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(999.dp))
                .background(Surface.copy(alpha = 0.85f)).border(1.dp, Border, RoundedCornerShape(999.dp))
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SdsLogo(34)
            Spacer(Modifier.width(12.dp))
            Text(title, color = TextPrimary, fontFamily = DisplayFamily, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable { open = !open },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary) }
        }
        
        // Dropdown Menu
        if (open) {
            Spacer(Modifier.height(60.dp))
            Column(
                Modifier.fillMaxWidth().padding(top = 60.dp).clip(RoundedCornerShape(16.dp)).background(Surface.copy(alpha = 0.95f))
                    .border(1.dp, Border2, RoundedCornerShape(16.dp)).padding(8.dp)
            ) {
                items.forEach { it2 ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { open = false; it2.onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it2.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
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
                contentColor = TextPrimary, edgePadding = 8.dp, modifier = Modifier.fillMaxWidth()
            ) {
                sessions.forEachIndexed { i, _ ->
                    Tab(
                        selected = i == activeIndex,
                        onClick = { activeIndex = i; manager.setActive(i) },
                        text = { Text("shell ${i + 1}", fontFamily = MonoFamily, fontSize = 12.sp) },
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
                    fontSize = 14.sp
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Starting shell…", color = Color.Gray, fontFamily = MonoFamily)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun HomeScreen(onTerminal: () -> Unit, onFiles: () -> Unit, onPackages: () -> Unit, onSecurity: () -> Unit) {
    val tiles = listOf(
        Triple("Terminal", Icons.Default.Terminal, onTerminal),
        Triple("File Browser", Icons.Default.Folder, onFiles),
        Triple("Linux Packages", Icons.Default.Memory, onPackages),
        Triple("Permissions", Icons.Default.Security, onSecurity)
    )
    
    Box(Modifier.fillMaxSize().background(Bg)) {
        // Soft, premium radial gradient background
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(Color(0xFF1A1A24), Bg),
            center = androidx.compose.ui.geometry.Offset(0f, 0f),
            radius = 1500f
        )))
        
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            
            // Hero Section with Image
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface.copy(alpha = 0.4f))
                    .border(1.dp, Border2, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageUrl,
                    contentDescription = "SDS Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Border2, RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SDS Terminal", color = TextPrimary, fontFamily = DisplayFamily, fontSize = 28.sp)
                        Spacer(Modifier.width(8.dp))
                        Pill("v1.0")
                    }
                    Text("A premium native Android terminal.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                        Spacer(Modifier.width(6.dp))
                        Text("System Ready", color = Green, fontFamily = MonoFamily, fontSize = 11.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp)); SectionLabel("Quick Actions")
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.forEach { (label, icon, onClick) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface.copy(alpha = 0.6f))
                            .border(1.dp, Border2, RoundedCornerShape(16.dp)).clickable { onClick() }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("→", color = TextQuaternary, fontFamily = MonoFamily, fontSize = 16.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp)); SectionLabel("Environment")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface.copy(alpha = 0.6f))
                    .border(1.dp, Border2, RoundedCornerShape(16.dp)).padding(20.dp)
            ) {
                Text(
                    "SDS Terminal runs a full Ubuntu Linux userspace in userspace using proot. No root required. On first launch, it will silently download and extract Linux directly inside the terminal.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 22.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = TextQuaternary, fontFamily = MonoFamily, fontSize = 11.sp,
        letterSpacing = 1.6.sp, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
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
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }, enabled = currentDir != rootDir) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = TextPrimary)
            }
            Text(currentDir.name.ifEmpty { "/" }, color = TextPrimary, fontSize = 18.sp, fontFamily = DisplayFamily)
        }
        Text(currentDir.absolutePath, color = TextTertiary, fontSize = 11.sp, fontFamily = MonoFamily, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        
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
                Text("  ${file.name}", color = TextPrimary, fontSize = 15.sp, fontFamily = MonoFamily)
            }
            HorizontalDivider(color = Border)
        }
    }
}

@Composable
fun SecurityScreen() {
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Permissions", fontFamily = DisplayFamily, fontSize = 26.sp, color = TextPrimary)
        Text(
            "SDS Terminal runs a Linux userspace inside proot. It does not grant root access to the Android system. Commands only run when typed.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 24.sp, modifier = Modifier.padding(top = 16.dp)
        )
        Text("Storage Access", fontSize = 18.sp, fontFamily = DisplayFamily, color = TextPrimary, modifier = Modifier.padding(top = 32.dp))
        Text(
            "To access your Android files (like /sdcard) inside the Linux environment, you must grant 'All Files Access' when prompted by Android.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun PackagesScreen(onInstall: (String) -> Unit) {
    var pkg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg).imePadding().padding(20.dp)) {
        Text("Package Manager", fontFamily = DisplayFamily, fontSize = 26.sp, color = TextPrimary)
        Text("Install packages inside the Ubuntu environment using apt.", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        
        androidx.compose.material3.OutlinedTextField(
            value = pkg, onValueChange = { pkg = it }, label = { Text("Package name (e.g. python3)") },
            singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = Border2,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
        androidx.compose.material3.Button(
            onClick = { if (pkg.isNotBlank()) onInstall(pkg.trim()) },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(50.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Bg),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Install Package", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun RootNav(manager: SessionManager, rootDir: File) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route ?: "home"
    val showNav = current != "terminal"
    
    Box(Modifier.fillMaxSize().background(Bg)) {
        NavHost(
            navController = nav, startDestination = "home",
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(top = if (showNav) 76.dp else 0.dp)
        ) {
            composable("home") {
                HomeScreen(
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
            composable("security") { SecurityScreen() }
        }
        
        // Floating Web-Style Nav Bar
        if (showNav) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                SdsNav("SDS Terminal", listOf(
                    NavItem("Home") { if (current != "home") nav.navigate("home") { launchSingleTop = true } },
                    NavItem("Terminal") { nav.navigate("terminal") { launchSingleTop = true } },
                    NavItem("File Browser") { nav.navigate("files") { launchSingleTop = true } },
                    NavItem("Linux Packages") { nav.navigate("packages") { launchSingleTop = true } },
                    NavItem("Permissions") { nav.navigate("security") { launchSingleTop = true } }
                ))
            }
        }
    }
}