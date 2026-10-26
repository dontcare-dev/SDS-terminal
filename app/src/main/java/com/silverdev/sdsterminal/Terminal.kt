package com.silverdev.sdsterminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

object TermColors {
    private val base = intArrayOf(
        0xFF0D0D0D.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
        0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
        0xFF666666.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
        0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFFFFFFF.toInt()
    )
    private val ext = IntArray(256).also { arr ->
        for (i in 0 until 16) arr[i] = base[i]
        for (i in 16 until 232) {
            val n = i - 16
            val r = (n / 36) % 6; val g = (n / 6) % 6; val b = n % 6
            val ri = if (r == 0) 0 else 55 + r * 40
            val gi = if (g == 0) 0 else 55 + g * 40
            val bi = if (b == 0) 0 else 55 + b * 40
            arr[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        for (i in 232 until 256) {
            val v = 8 + (i - 232) * 10
            arr[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    fun color(i: Int): Color = Color(ext[i.coerceIn(0, 255)])
    fun defaultBg(): Color = Color(0xFF0D0D0D)
    fun defaultFg(): Color = Color(0xFFE5E5E5)
}

class Cell(var char: Char = ' ', var fg: Int = 7, var bg: Int = 0, var bold: Boolean = false)

class TermBuffer(var rows: Int = 24, var cols: Int = 80, val maxScrollback: Int = 5000) {
    var grid: Array<Array<Cell>> = Array(rows) { Array(cols) { Cell() } }
    val scrollback: ArrayDeque<Array<Cell>> = ArrayDeque()
    var cursorRow = 0; var cursorCol = 0
    var savedRow = 0; var savedCol = 0
    var currentFg = 7; var currentBg = 0; var currentBold = false
    var revision by mutableIntStateOf(0); private set

    fun resize(nr: Int, nc: Int) {
        val ng = Array(nr) { r -> Array(nc) { c ->
            if (r < rows && c < cols) grid[r][c] else Cell(fg = currentFg, bg = currentBg)
        } }
        grid = ng; rows = nr; cols = nc
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        bump()
    }
    fun clear() { for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = Cell(fg = currentFg, bg = currentBg); cursorRow = 0; cursorCol = 0; bump() }
    fun clearLine() { for (c in 0 until cols) grid[cursorRow][c] = Cell(fg = currentFg, bg = currentBg); bump() }
    fun clearLineFromCursor() { for (c in cursorCol until cols) grid[cursorRow][c] = Cell(fg = currentFg, bg = currentBg); bump() }
    fun clearLineToCursor() { for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = Cell(fg = currentFg, bg = currentBg); bump() }
    fun eraseFromCursor() {
        for (c in cursorCol until cols) grid[cursorRow][c] = Cell(fg = currentFg, bg = currentBg)
        for (r in cursorRow + 1 until rows) for (c in 0 until cols) grid[r][c] = Cell(fg = currentFg, bg = currentBg); bump()
    }
    fun eraseToCursor() {
        for (r in 0 until cursorRow) for (c in 0 until cols) grid[r][c] = Cell(fg = currentFg, bg = currentBg)
        for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = Cell(fg = currentFg, bg = currentBg); bump()
    }
    fun scrollUp() {
        scrollback.addLast(grid[0]); while (scrollback.size > maxScrollback) scrollback.removeFirst()
        for (r in 0 until rows - 1) grid[r] = grid[r + 1]
        grid[rows - 1] = Array(cols) { Cell(fg = currentFg, bg = currentBg) }; bump()
    }
    fun newline() { cursorRow++; if (cursorRow >= rows) { cursorRow = rows - 1; scrollUp() }; bump() }
    fun cr() { cursorCol = 0; bump() }
    fun backspace() { if (cursorCol > 0) cursorCol--; bump() }
    fun tab() { cursorCol = ((cursorCol / 8 + 1) * 8).coerceAtMost(cols - 1); bump() }
    fun putChar(c: Char) {
        if (cursorCol >= cols) { cursorCol = 0; newline() }
        grid[cursorRow][cursorCol] = Cell(c, currentFg, currentBg, currentBold); cursorCol++; bump()
    }
    fun moveCursor(r: Int, c: Int) { cursorRow = r.coerceIn(0, rows - 1); cursorCol = c.coerceIn(0, cols - 1); bump() }
    fun up(n: Int) = moveCursor(cursorRow - n, cursorCol)
    fun down(n: Int) = moveCursor(cursorRow + n, cursorCol)
    fun right(n: Int) = moveCursor(cursorRow, cursorCol + n)
    fun left(n: Int) = moveCursor(cursorRow, cursorCol - n)
    fun bump() { revision++ }
}

class AnsiParser(private val buffer: TermBuffer) {
    private enum class St { GROUND, ESCAPE, CSI, OSC, CHARSET }
    private var state = St.GROUND
    private val params = IntArray(16)
    private var pCount = 0
    private var pCurrent = -1
    private val decoder = Charset.forName("UTF-8").newDecoder()
    private val charBuf = CharBuffer.allocate(16384)

    fun feed(bytes: ByteArray, len: Int) {
        val bb = ByteBuffer.wrap(bytes, 0, len)
        charBuf.clear(); decoder.decode(bb, charBuf, false); charBuf.flip()
        while (charBuf.hasRemaining()) process(charBuf.get())
    }
    private fun reset() { pCount = 0; pCurrent = -1 }
    private fun commit() {
        if (pCurrent >= 0) { if (pCount < params.size) params[pCount++] = pCurrent; pCurrent = -1 }
        else if (pCount < params.size) params[pCount++] = 0
    }
    private fun process(c: Char) {
        when (state) {
            St.GROUND -> ground(c); St.ESCAPE -> esc(c); St.CSI -> csi(c)
            St.OSC -> { if (c == '\u0007') state = St.GROUND }
            St.CHARSET -> state = St.GROUND
        }
    }
    private fun ground(c: Char) {
        when (c) {
            '\u0007' -> {}; '\b' -> buffer.backspace(); '\t' -> buffer.tab()
            '\n' -> buffer.newline(); '\r' -> buffer.cr()
            '\u001B' -> { reset(); state = St.ESCAPE }
            else -> if (c.code >= 0x20) buffer.putChar(c)
        }
    }
    private fun esc(c: Char) {
        when (c) {
            '[' -> { reset(); state = St.CSI }
            ']' -> { reset(); state = St.OSC }
            '(', ')' -> { reset(); state = St.CHARSET }
            '7' -> { buffer.savedRow = buffer.cursorRow; buffer.savedCol = buffer.cursorCol; state = St.GROUND }
            '8' -> { buffer.moveCursor(buffer.savedRow, buffer.savedCol); state = St.GROUND }
            'M' -> { if (buffer.cursorRow > 0) buffer.up(1); state = St.GROUND }
            'D' -> { buffer.newline(); state = St.GROUND }
            'E' -> { buffer.cr(); buffer.newline(); state = St.GROUND }
            else -> state = St.GROUND
        }
    }
    private fun csi(c: Char) {
        when {
            c.isDigit() -> { if (pCurrent < 0) pCurrent = 0; pCurrent = pCurrent * 10 + (c - '0') }
            c == ';' -> commit()
            c.code in 0x40..0x7E -> { commit(); dispatch(c); state = St.GROUND }
            else -> {}
        }
    }
    private fun p(i: Int, d: Int) = if (i < pCount && params[i] > 0) params[i] else d
    private fun dispatch(f: Char) {
        when (f) {
            'A' -> buffer.up(p(0, 1)); 'B' -> buffer.down(p(0, 1))
            'C' -> buffer.right(p(0, 1)); 'D' -> buffer.left(p(0, 1))
            'E' -> { buffer.cr(); buffer.down(p(0, 1)) }
            'F' -> { buffer.cr(); buffer.up(p(0, 1)) }
            'G' -> buffer.moveCursor(buffer.cursorRow, p(0, 1) - 1)
            'H', 'f' -> buffer.moveCursor(p(0, 1) - 1, p(1, 1) - 1)
            'J' -> when (p(0, 0)) { 0 -> buffer.eraseFromCursor(); 1 -> buffer.eraseToCursor(); 2 -> buffer.clear() }
            'K' -> when (p(0, 0)) { 0 -> buffer.clearLineFromCursor(); 1 -> buffer.clearLineToCursor(); 2 -> buffer.clearLine() }
            'm' -> sgr()
        }
    }
    private fun sgr() {
        if (pCount == 0) { resetSgr(); return }
        var i = 0
        while (i < pCount) {
            val v = params[i]
            when {
                v == 0 -> resetSgr()
                v == 1 -> buffer.currentBold = true; v == 22 -> buffer.currentBold = false
                v in 30..37 -> buffer.currentFg = v - 30
                v in 40..47 -> buffer.currentBg = v - 40
                v in 90..97 -> buffer.currentFg = v - 90 + 8
                v in 100..107 -> buffer.currentBg = v - 100 + 8
                v == 39 -> buffer.currentFg = 7; v == 49 -> buffer.currentBg = 0
                v == 38 && i + 2 < pCount && params[i + 1] == 5 -> { buffer.currentFg = params[i + 2]; i += 2 }
                v == 48 && i + 2 < pCount && params[i + 1] == 5 -> { buffer.currentBg = params[i + 2]; i += 2 }
            }
            i++
        }
    }
    private fun resetSgr() { buffer.currentFg = 7; buffer.currentBg = 0; buffer.currentBold = false }
}

@Composable
fun TerminalRenderer(
    buffer: TermBuffer,
    onInput: (ByteArray) -> Unit,
    onResize: (Int, Int) -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    fontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize) { TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize, color = TermColors.defaultFg()) }
    val m = remember(style) { measurer.measure("M", style) }
    val cw = m.size.width.toFloat().coerceAtLeast(1f)
    val ch = m.size.height.toFloat().coerceAtLeast(1f)
    var textValue by remember { mutableStateOf("") }
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(500); cursorOn = !cursorOn } }

    Box(
        modifier = modifier.fillMaxSize()
            .onPreviewKeyEvent(onKeyEvent)
            .onSizeChanged { s ->
                val r = (s.height / ch).toInt().coerceAtLeast(1)
                val c = (s.width / cw).toInt().coerceAtLeast(1)
                if (r != buffer.rows || c != buffer.cols) onResize(r, c)
            }
            .pointerInput(Unit) { detectTapGestures { fr.requestFocus(); kb?.show() } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            buffer.revision
            for (r in 0 until buffer.rows) for (c in 0 until buffer.cols) {
                val cell = buffer.grid[r][c]
                val x = c * cw; val y = r * ch
                if (cell.bg != 0) drawRect(TermColors.color(cell.bg), Offset(x, y), Size(cw, ch))
                if (cell.char != ' ') drawText(measurer, cell.char.toString(), Offset(x, y), style = style.copy(color = TermColors.color(cell.fg)))
            }
            if (cursorOn) drawRect(TermColors.defaultFg().copy(alpha = 0.6f), Offset(buffer.cursorCol * cw, buffer.cursorRow * ch), Size(cw, ch))
        }
        BasicTextField(
            value = textValue,
            onValueChange = { n ->
                if (n.length > textValue.length) onInput(n.substring(textValue.length).toByteArray())
                textValue = ""
            },
            singleLine = true,
            textStyle = style.copy(color = Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.None),
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(fr)
        )
    }
}

private val extraKeys = listOf(
    "ESC" to "\u001B", "TAB" to "\t", "←" to "\u001B[D", "↑" to "\u001B[A",
    "↓" to "\u001B[B", "→" to "\u001B[C", "HOME" to "\u001B[H", "END" to "\u001B[F",
    "PGUP" to "\u001B[5~", "PGDN" to "\u001B[6~", "CTRL+C" to "\u0003",
    "CTRL+D" to "\u0004", "CTRL+L" to "\u000C", "CTRL+Z" to "\u001A"
)

@Composable
fun ExtraKeysRow(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color(0xFF101114))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        extraKeys.forEach { (label, v) ->
            TextButton(onClick = { onSend(v) }, shape = RoundedCornerShape(6.dp), modifier = Modifier.height(36.dp)) {
                Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFC9CCD3))
            }
        }
    }
}