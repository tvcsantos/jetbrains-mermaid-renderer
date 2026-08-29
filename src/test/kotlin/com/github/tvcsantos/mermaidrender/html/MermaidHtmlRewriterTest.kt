package com.github.tvcsantos.mermaidrender.html

import com.github.tvcsantos.mermaidrender.render.CachedDiagram
import com.github.tvcsantos.mermaidrender.render.DiagramRequest
import com.github.tvcsantos.mermaidrender.render.DiagramState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class MermaidHtmlRewriterTest {

    private val requested = mutableListOf<DiagramRequest>()

    private fun request(source: String) = DiagramRequest(
        source = source,
        theme = "default",
        background = "#ffffff",
        maxWidth = 760,
        scale = 2.0,
        mermaidVersion = "test",
    ).also { requested += it }

    private fun rewrite(html: String, heuristics: Boolean = true, state: (DiagramRequest) -> DiagramState) =
        MermaidHtmlRewriter.rewrite(html, heuristics, ::request, state).html

    @Test
    fun `a ready diagram becomes an image`() {
        val diagram = CachedDiagram(Path.of("/tmp/mermaid/abc-400x300.png"), 400, 300)
        val result = rewrite("<html><body><pre class=\"mermaid\">graph TD; A-->B;</pre></body></html>") {
            DiagramState.Ready(diagram)
        }

        assertTrue(result.contains("<img"))
        assertTrue(result.contains("width=\"400\""))
        assertTrue(result.contains("height=\"300\""))
        assertTrue(result.contains(diagram.url))
        assertFalse(result.contains("graph TD"))
        assertEquals(listOf("graph TD; A-->B;"), requested.map { it.source })
    }

    @Test
    fun `a pending diagram keeps the source and adds a note`() {
        val result = rewrite("<html><body><pre class=\"mermaid\">graph TD; A-->B;</pre></body></html>") {
            DiagramState.Pending
        }

        assertTrue(result.contains("graph TD"))
        assertTrue(result.contains("Rendering diagram"))
    }

    @Test
    fun `a failed diagram reports the mermaid message`() {
        val result = rewrite("<html><body><pre class=\"mermaid\">nonsense</pre></body></html>") {
            DiagramState.Failed("Parse error on line 1")
        }

        assertTrue(result.contains("nonsense"))
        assertTrue(result.contains("Parse error on line 1"))
    }

    @Test
    fun `documentation without diagrams is returned untouched`() {
        val html = "<html><body><p>Hello</p><pre><code>val x = 1</code></pre></body></html>"
        val result = rewrite(html) { DiagramState.Pending }

        assertEquals(html, result)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `every diagram in a comment is handled`() {
        val diagram = CachedDiagram(Path.of("/tmp/mermaid/abc-10x10.png"), 10, 10)
        val result = rewrite(
            "<html><body>" +
                "<pre class=\"mermaid\">graph TD; A-->B;</pre>" +
                "<pre class=\"mermaid\">graph LR; C-->D;</pre>" +
                "</body></html>"
        ) { DiagramState.Ready(diagram) }

        assertEquals(2, result.split("<img").size - 1)
        assertEquals(2, requested.size)
    }
}
