package com.github.tvcsantos.mermaidrenderer.html

import com.github.tvcsantos.mermaidrenderer.render.CachedDiagram
import com.github.tvcsantos.mermaidrenderer.render.DiagramRequest
import com.github.tvcsantos.mermaidrenderer.render.DiagramState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

internal class MermaidHtmlRewriterTest {

    private val requested = mutableListOf<DiagramRequest>()

    private fun request(source: String) = DiagramRequest(
        source = source,
        theme = "default",
        background = "#ffffff",
        maxWidth = 760,
        scale = 2.0,
        mermaidVersion = "test",
    ).also { requested += it }

    private fun rewrite(html: String, state: (DiagramRequest) -> DiagramState) =
        MermaidHtmlRewriter.rewrite(html, heuristics = true, requestFor = ::request, resolve = state).html

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
    fun `a pending diagram is silent by default`() {
        val html = "<html><body><pre class=\"mermaid\">graph TD; A-->B;</pre></body></html>"

        val result = rewrite(html) { DiagramState.Pending }

        assertEquals("The comment must not change until the diagram is ready", html, result)
    }

    @Test
    fun `a pending diagram is announced when progress is shown`() {
        val result = MermaidHtmlRewriter.rewrite(
            html = "<html><body><pre class=\"mermaid\">graph TD; A-->B;</pre></body></html>",
            heuristics = true,
            requestFor = ::request,
            resolve = { DiagramState.Pending },
            showProgress = true,
        )

        assertTrue(result.html.contains("graph TD"))
        assertTrue(result.html.contains("Rendering diagram"))
    }

    @Test
    fun `a failed diagram leaves the comment untouched`() {
        val html = "<html><body><pre class=\"mermaid\">nonsense</pre></body></html>"

        val result = rewrite(html) { DiagramState.Failed }

        // Reported by the gutter line marker and the log, never pasted into the documentation.
        assertEquals(html, result)
    }

    @Test
    fun `a failed diagram is reported so the gutter marker can follow`() {
        val result = MermaidHtmlRewriter.rewrite(
            html = "<html><body><pre class=\"mermaid\">graph TD;\n  A --&gt;</pre></body></html>",
            heuristics = true,
            requestFor = ::request,
            resolve = { DiagramState.Failed },
        )

        assertEquals(setOf("graph TD;\nA -->"), result.failed)
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
