package dev.herdr.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private val base16 = longArrayOf(
    0xFF3B3F46, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFDCDFE4,
    0xFF6B7079, 0xFFFF7A85, 0xFFB5E890, 0xFFFFD68A, 0xFF8CC8FF, 0xFFE09CF5, 0xFF7FD6E0, 0xFFFFFFFF,
).map { Color(it) }

private fun xterm256(n: Int): Color = when {
    n < 16 -> base16[n]
    n < 232 -> {
        val i = n - 16
        fun c(v: Int) = if (v == 0) 0 else 55 + v * 40
        Color(c(i / 36), c(i / 6 % 6), c(i % 6))
    }
    else -> (8 + (n - 232) * 10).let { Color(it, it, it) }
}

// CSI (ESC [ ... final) and OSC (ESC ] ... BEL | ESC \) sequences.
private val escape = Regex("\u001B\\[([0-?]*)[ -/]*([@-~])|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)?|\u001B.")

/** Renders SGR colors/bold/italic/underline; drops every other escape sequence. */
fun ansiToAnnotated(raw: String): AnnotatedString = buildAnnotatedString {
    val s = raw.replace("\r\n", "\n")
    var fg: Color? = null
    var bg: Color? = null
    var bold = false
    var dim = false
    var italic = false
    var underline = false
    var at = 0
    fun flush(end: Int) {
        if (end <= at) return
        val start = length
        append(s, at, end)
        if (fg != null || bg != null || bold || dim || italic || underline) {
            addStyle(
                SpanStyle(
                    color = (fg ?: Color.Unspecified).let { if (dim && it != Color.Unspecified) it.copy(alpha = 0.6f) else it },
                    background = bg ?: Color.Unspecified,
                    fontWeight = if (bold) FontWeight.SemiBold else null,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    textDecoration = if (underline) TextDecoration.Underline else null,
                ),
                start, length,
            )
        }
    }
    for (m in escape.findAll(s)) {
        flush(m.range.first)
        at = m.range.last + 1
        if (m.groupValues[2] != "m") continue
        val codes = m.groupValues[1].split(';', ':').map { it.toIntOrNull() ?: 0 }.iterator()
        while (codes.hasNext()) {
            when (val c = codes.next()) {
                0 -> { fg = null; bg = null; bold = false; dim = false; italic = false; underline = false }
                1 -> bold = true
                2 -> dim = true
                3 -> italic = true
                4 -> underline = true
                22 -> { bold = false; dim = false }
                23 -> italic = false
                24 -> underline = false
                in 30..37 -> fg = base16[c - 30]
                in 90..97 -> fg = base16[c - 82]
                39 -> fg = null
                in 40..47 -> bg = base16[c - 40]
                in 100..107 -> bg = base16[c - 92]
                49 -> bg = null
                38, 48 -> {
                    val color = when (if (codes.hasNext()) codes.next() else -1) {
                        5 -> if (codes.hasNext()) xterm256(codes.next().coerceIn(0, 255)) else null
                        2 -> {
                            val rgb = List(3) { if (codes.hasNext()) codes.next().coerceIn(0, 255) else 0 }
                            Color(rgb[0], rgb[1], rgb[2])
                        }
                        else -> null
                    }
                    if (c == 38) fg = color else bg = color
                }
            }
        }
    }
    flush(s.length)
}
