package com.github.tvcsantos.mermaidrenderer.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagramErrorTextTest {

    @Suppress("SpellCheckingInspection")
    private val realMessage = """
        Parse error on line 3:
        graph TD; A -->
        ----------------^
        Expecting 'AMP', 'COLON', 'PIPE', 'TESTSTR', 'DOWN', 'DEFAULT', 'NUM', 'COMMA', 'NODE_STRING', 'BRKT', 'MINUS', 'MULT', 'UNICODE_TEXT', got 'EOF'
    """.trimIndent()

    @Test
    fun `no display line is wider than the limit`() {
        val lines = DiagramErrorText.wrap(realMessage)

        assertTrue("Too wide: ${lines.maxByOrNull { it.length }}", lines.all { it.length <= 72 })
    }

    @Test
    fun `wrapping happens at word boundaries`() {
        val lines = DiagramErrorText.wrap(realMessage)

        assertTrue("A line was cut mid-word: $lines", lines.none { it.endsWith("'") && it.length == 72 })
        assertTrue("The message lost its tail: $lines", lines.joinToString(" ").contains("got 'EOF'"))
    }

    @Test
    fun `short messages are left alone`() {
        assertEquals(listOf("Parse error on line 1"), DiagramErrorText.wrap("Parse error on line 1"))
    }

    @Test
    fun `very long messages are capped`() {
        val lines = DiagramErrorText.wrap((1..500).joinToString(" ") { "token$it" })

        assertEquals(13, lines.size)
        assertEquals("...", lines.last())
    }

    @Test
    fun `html escapes and breaks lines`() {
        val html = DiagramErrorText.toHtml(listOf("a --> b\nsecond line"))

        assertEquals("a --&gt; b<br>second line", html)
    }
}
