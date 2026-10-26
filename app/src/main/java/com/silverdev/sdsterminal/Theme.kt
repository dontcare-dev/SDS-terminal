package com.silverdev.sdsterminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

val Bg = Color(0xFF050505)
val Surface = Color(0xFF0A0A0B)
val Surface2 = Color(0xFF0F0F11)
val Surface3 = Color(0xFF161618)
val Border = Color(0xFF1B1B1E)
val Border2 = Color(0xFF2B2B30)
val TextPrimary = Color(0xFFEDEDF0)
val TextSecondary = Color(0xFF9B9BA3)
val TextTertiary = Color(0xFF6A6A73)
val TextQuaternary = Color(0xFF45454D)
val Green = Color(0xFF3ECF8E)
val Red = Color(0xFFF2555A)
val DisplayFamily = FontFamily.Cursive
val MonoFamily = FontFamily.Monospace
val TerminalBlack = Color(0xFF0D0D0D)
val TerminalExtraRow = Color(0xFF101114)

private val DarkColors = darkColorScheme(
    primary = TextPrimary, onPrimary = Bg, secondary = Green,
    background = Bg, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = Surface3, onSurfaceVariant = TextSecondary,
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
        Icon(Icons.Default.Terminal, contentDescription = null, tint = TextSecondary, modifier = Modifier.size((size * 0.6f).dp))
    }
}

@Composable
fun PixelField(modifier: Modifier = Modifier, pixelScale: Float = 4f) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(60); t += 0.06f } }
    val ramp = remember {
        arrayOf(Color(0xFF030304), Color(0xFF0D0E12), Color(0xFF1C1E26), Color(0xFF3A3E4B), Color(0xFF737A8E), Color(0xFFBEC4D7))
    }
    Canvas(modifier) {
        val pw = size.width / pixelScale; val ph = size.height / pixelScale
        if (pw < 2f || ph < 2f) return@Canvas
        val cols = pw.toInt(); val rows = ph.toInt()
        for (y in 0 until rows) for (x in 0 until cols) {
            val xf = x * 0.020f; val yf = y * 0.020f
            val w1 = sin(xf * 0.85f + yf * 0.65f + t * 0.45f)
            val w2 = cos(xf * 0.70f - yf * 0.90f - t * 0.35f)
            val w3 = sin((xf + yf) * 0.60f + t * 0.55f)
            var nw = 0f
            val pts = arrayOf(0.15f to 0.25f, 0.85f to 0.30f, 0.30f to 0.75f)
            pts.forEach { (px, py) ->
                val d = sqrt((x - pw * px) * (x - pw * px) + (y - ph * py) * (y - ph * py))
                nw += cos(d * 0.035f - t * 0.9f) * exp(-d * 0.007f)
            }
            var q = (w1 * 0.25f + w2 * 0.22f + w3 * 0.18f + nw * 0.35f + 1.6f) * 0.24f
            if (q < 0f) q = 0f
            q = Math.pow(q.toDouble(), 1.25).toFloat() * 1.1f
            if (q > 1f) q = 1f
            val idx = floor(q * (ramp.size - 1)).toInt().coerceIn(0, ramp.size - 1)
            if (idx > 0) drawRect(ramp[idx], Offset(x * pixelScale, y * pixelScale), Size(pixelScale, pixelScale))
        }
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

data class NavItem(val label: String, val code: String, val onClick: () -> Unit)

@Composable
fun SdsNav(title: String, items: List<NavItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(999.dp))
                .background(Surface.copy(alpha = 0.85f)).border(1.dp, Border, RoundedCornerShape(999.dp))
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SdsLogo(26)
            Spacer(Modifier.width(9.dp))
            Text(title, color = TextPrimary, fontFamily = DisplayFamily, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(999.dp)).clickable { open = !open },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextSecondary) }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface.copy(alpha = 0.95f))
                    .border(1.dp, Border, RoundedCornerShape(14.dp)).padding(6.dp)
            ) {
                items.forEach { it2 ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .clickable { open = false; it2.onClick() }.padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(it2.label, color = TextSecondary, fontFamily = DisplayFamily, fontSize = 16.sp)
                        Text(it2.code, color = TextQuaternary, fontFamily = MonoFamily, fontSize = 10.sp)
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
        Triple("Files", Icons.Default.Folder, onFiles),
        Triple("Packages", Icons.Default.Description, onPackages),
        Triple("Security", Icons.Default.Menu, onSecurity)
    )
    Box(Modifier.fillMaxSize().background(Bg)) {
        PixelField(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.9f), Bg))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionLabel("Profile")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SdsLogo(58)
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SDS Terminal", color = TextPrimary, fontFamily = DisplayFamily, fontSize = 26.sp)
                        Spacer(Modifier.width(8.dp))
                        Pill("SDS")
                    }
                    Text("A terminal that feels like you.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(Green))
                        Spacer(Modifier.width(6.dp))
                        Text("Available  ·  Local  ·  Offline-first", color = TextSecondary, fontFamily = MonoFamily, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(28.dp)); SectionLabel("Quick actions")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { (label, icon, onClick) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(10.dp)).clickable { onClick() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        Text("↗", color = TextQuaternary, fontFamily = MonoFamily, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(28.dp)); SectionLabel("Recent sessions")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(18.dp)
            ) {
                Text("No recent sessions yet.", color = TextTertiary, fontFamily = MonoFamily, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text("Tap Terminal to start your first shell.", color = TextPrimary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(28.dp)); SectionLabel("About")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(18.dp)
            ) {
                Text(
                    "SDS Terminal is a small, hand-built Android terminal from Silver Dev Studios. Real PTY, real shell. Runs Android shell by default; Linux rootfs when proot is bundled.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = TextQuaternary, fontFamily = MonoFamily, fontSize = 10.5.sp,
        letterSpacing = 1.6.sp, modifier = Modifier.padding(bottom = 12.dp)
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
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }, enabled = currentDir != rootDir) {
                Icon(Icons.Default.Menu, contentDescription = "Up", tint = TextPrimary)
            }
            Text(currentDir.name.ifEmpty { "/" }, color = TextPrimary, fontSize = 15.sp, fontFamily = MonoFamily)
        }
        Text(currentDir.absolutePath, color = TextTertiary, fontSize = 11.sp, fontFamily = MonoFamily, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        entries.forEach { file ->
            Row(
                Modifier.fillMaxWidth().clickable { if (file.isDirectory) currentDir = file }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    null, tint = if (file.isDirectory) TextPrimary else TextTertiary, modifier = Modifier.size(22.dp)
                )
                Text("  ${file.name}", color = TextPrimary, fontSize = 14.sp, fontFamily = MonoFamily)
            }
            HorizontalDivider(color = Border)
        }
    }
}

@Composable
fun SecurityScreen() {
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Security", fontFamily = DisplayFamily, fontSize = 22.sp, color = TextPrimary)
        Text(
            "SDS Terminal runs a Linux userspace inside proot when bundled. proot is a userspace chroot and bind-mount emulation, not a kernel sandbox. It does not grant root. Commands only run when typed. Nothing is silently executed.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp)
        )
        Text("Storage permissions", fontSize = 16.sp, color = TextPrimary, modifier = Modifier.padding(top = 24.dp))
        Text(
            "No broad storage permission is requested. The rootfs lives in the app's private files directory.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun PackagesScreen(onInstall: (String) -> Unit) {
    var pkg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg).imePadding().padding(20.dp)) {
        Text("Package Manager", fontFamily = DisplayFamily, fontSize = 22.sp, color = TextPrimary)
        Text("Installs run inside the proot session using apt.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
        androidx.compose.material3.OutlinedTextField(
            value = pkg, onValueChange = { pkg = it }, label = { Text("Package name") },
            singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
        )
        androidx.compose.material3.Button(
            onClick = { if (pkg.isNotBlank()) onInstall(pkg.trim()) },
            modifier = Modifier.padding(top = 12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Bg),
            shape = RoundedCornerShape(10.dp)
        ) { Text("Install", fontFamily = MonoFamily) }
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
        if (showNav) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                SdsNav("SDS Terminal", listOf(
                    NavItem("Home", "00") { if (current != "home") nav.navigate("home") { launchSingleTop = true } },
                    NavItem("Terminal", "01") { nav.navigate("terminal") { launchSingleTop = true } },
                    NavItem("Files", "02") { nav.navigate("files") { launchSingleTop = true } },
                    NavItem("Packages", "03") { nav.navigate("packages") { launchSingleTop = true } },
                    NavItem("Security", "04") { nav.navigate("security") { launchSingleTop = true } }
                ))
            }
        }
    }
}