package dev.herdr.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {
    @Test
    fun quotesForPosixShell() {
        assertEquals("'it'\\''s; rm -rf ~'", shellQuote("it's; rm -rf ~"))
        assertEquals(
            "PATH=\"\$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:\$PATH\" HERDR_SESSION='work' herdr 'agent' 'prompt' 'w1:p1' 'hi \$(x)'",
            herdrCommand("work", listOf("agent", "prompt", "w1:p1", "hi \$(x)")),
        )
    }

    @Test
    fun parsesSnapshot() {
        val out = """{"id":"x","result":{"snapshot":{
          "workspaces":[{"workspace_id":"w7","label":"api","agent_status":"blocked"}],
          "tabs":[{"tab_id":"w7:t1","workspace_id":"w7","label":"1"}],
          "panes":[
            {"pane_id":"w7:p1","workspace_id":"w7","tab_id":"w7:t1","agent":"claude","agent_status":"blocked","cwd":"/r","terminal_title_stripped":"t"},
            {"pane_id":"w7:p2","workspace_id":"w7","tab_id":"w7:t1","agent":null,"cwd":"/r"}
          ]}},"type":"session_snapshot"}"""
        val s = parseSnapshot(out)
        assertEquals("api", s.workspace("w7")?.label)
        assertEquals(listOf("w7:p1"), s.agents.map { it.id })
        assertEquals("blocked", s.agents[0].status)
        assertEquals("shell", s.panes[1].name)
        assertEquals("unknown", s.panes[1].status)
        assertEquals(listOf("default"), parseSessions("""{"sessions":[{"name":"default","running":true},{"name":"old","running":false}]}"""))
    }
}

class AnsiTest {
    @Test
    fun keepsTextAndStylesDropsOtherEscapes() {
        val a = dev.herdr.android.ui.ansiToAnnotated("\u001B]0;title\u0007\u001B[2K\u001B[1;31mred\u001B[0m plain \u001B[38;5;196mx\u001B[38;2;1;2;3my\u001B[m")
        assertEquals("red plain xy", a.text)
        assertEquals("a\nb", dev.herdr.android.ui.ansiToAnnotated("a\r\nb").text)
        val red = a.spanStyles.first { a.text.substring(it.start, it.end) == "red" }.item
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, red.fontWeight)
        assertEquals(3, a.spanStyles.size) // red, x, y; " plain " unstyled
        assertEquals(androidx.compose.ui.graphics.Color(1, 2, 3), a.spanStyles.last().item.color)
    }
}
