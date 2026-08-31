package com.github.tvcsantos.mermaidrenderer.html

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class MermaidBlockDetectorTest {

    private fun firstBlock(html: String) = Jsoup.parse(html).select("pre, .mermaid").first()!!

    @Test
    fun `detects a language-mermaid fence`() {
        val element = firstBlock("<pre><code class=\"language-mermaid\">graph TD;\n  A--&gt;B;</code></pre>")
        assertEquals("graph TD;\n  A-->B;", MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = false))
    }

    @Test
    fun `detects a pre with a mermaid class`() {
        val element = firstBlock("<pre class=\"mermaid\">sequenceDiagram\n  A->>B: hi</pre>")
        assertEquals("sequenceDiagram\n  A->>B: hi", MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = false))
    }

    @Test
    fun `strips a leading mermaid info line`() {
        val element = firstBlock("<pre><code>mermaid\nflowchart LR\n  A --&gt; B</code></pre>")
        assertEquals("flowchart LR\n  A --> B", MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = false))
    }

    @Test
    fun `heuristics pick up an untagged diagram`() {
        val element = firstBlock("<pre><code>stateDiagram-v2\n  [*] --&gt; Idle</code></pre>")
        assertNull(MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = false))
        assertEquals(
            "stateDiagram-v2\n  [*] --> Idle",
            MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = true),
        )
    }

    @Test
    fun `line breaks encoded as br are preserved`() {
        val element = firstBlock(
            "<pre><code><span>graph TD;<br></span><span>  A --&gt; B;</span></code></pre>"
        )
        assertEquals("graph TD;\n  A --> B;", MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = true))
    }

    @Test
    fun `a body tagged in the comment source is detected without heuristics`() {
        val element = firstBlock("<pre><code><span>graph TD;<br></span><span>  A --&gt; B;</span></code></pre>")

        assertNull(MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = false))
        assertEquals(
            "graph TD;\n  A --> B;",
            MermaidBlockDetector.getMermaidSourceOrNull(
                element,
                heuristics = false,
                isTagged = setOf("graph TD;\nA --> B;")::contains,
            ),
        )
    }

    @Test
    fun `leaves ordinary code alone`() {
        val element = firstBlock("<pre><code>val graphics = 1</code></pre>")
        assertNull(MermaidBlockDetector.getMermaidSourceOrNull(element, heuristics = true))
    }

    @Test
    fun `the keyword has to end where the declaration ends`() {
        assertTrue(MermaidBlockDetector.looksLikeMermaid("graph TD"))
        assertTrue(MermaidBlockDetector.looksLikeMermaid("gantt"))
        assertTrue("a keyword of its own in Mermaid's grammar", MermaidBlockDetector.looksLikeMermaid("gitGraph:"))
        assertTrue("a known variant", MermaidBlockDetector.looksLikeMermaid("stateDiagram-v2"))
        assertTrue(MermaidBlockDetector.looksLikeMermaid("flowchart-elk LR"))

        assertFalse("an identifier that merely starts with one", MermaidBlockDetector.looksLikeMermaid("graphics.draw()"))
        assertFalse(MermaidBlockDetector.looksLikeMermaid("pieChartBuilder()"))
    }

    @Test
    fun `ordinary code that starts with a keyword is not a diagram`() {
        assertFalse(MermaidBlockDetector.looksLikeMermaid("graph.addNode(x)"))
        assertFalse(MermaidBlockDetector.looksLikeMermaid("pie.slice(3)"))
        assertFalse(MermaidBlockDetector.looksLikeMermaid("timeline.push(event)"))
        @Suppress("SpellCheckingInspection")
        assertFalse(MermaidBlockDetector.looksLikeMermaid("gantt->render()"))
    }

    @Test
    fun `an invented variant is not guessed to be a diagram`() {
        assertFalse(MermaidBlockDetector.looksLikeMermaid("stateDiagram-v3"))
    }

    @Test
    fun `configuration that starts with a keyword is not a diagram`() {
        @Suppress("SpellCheckingInspection")
        assertFalse(MermaidBlockDetector.looksLikeMermaid("graph: mygraph"))
        assertFalse(MermaidBlockDetector.looksLikeMermaid("timeline: 2026"))
        assertFalse(MermaidBlockDetector.looksLikeMermaid("pie: 3"))
    }
}
