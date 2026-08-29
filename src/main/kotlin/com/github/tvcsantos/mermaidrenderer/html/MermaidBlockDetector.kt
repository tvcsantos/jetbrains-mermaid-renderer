package com.github.tvcsantos.mermaidrenderer.html

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Decides whether a rendered code block holds a Mermaid diagram.
 *
 * Doc comment renderers differ in what survives into the HTML: KDoc keeps the fence info string as
 * a `language-mermaid` class, Javadoc authors write `<pre class="mermaid">`, and some renderers
 * drop the marker entirely - hence the optional keyword heuristic.
 */
object MermaidBlockDetector {

    private val KEYWORDS = listOf(
        "graph",
        "flowchart",
        "sequencediagram",
        "classdiagram",
        "statediagram",
        "erdiagram",
        "journey",
        "gantt",
        "pie",
        "gitgraph",
        "mindmap",
        "timeline",
        "quadrantchart",
        "requirementdiagram",
        "c4context",
        "c4container",
        "c4component",
        "sankey-beta",
        "xychart-beta",
        "block-beta",
        "packet-beta",
        "architecture-beta",
        "kanban",
        "zenuml",
        "radar-beta",
        "treemap-beta",
    )

    /** Mermaid source of [element], or `null` when it is an ordinary code block. */
    fun mermaidSource(element: Element, heuristics: Boolean): String? {
        val code = element.selectFirst("code")
        val text = sourceOf(code ?: element)
        if (text.isBlank()) return null

        val classes = (element.className() + " " + code?.className().orEmpty()).lowercase()
        if (classes.contains("mermaid")) return text

        val lines = text.lines()
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstIndex < 0) return null
        val first = lines[firstIndex].trim()

        // Some renderers keep the fence info string as the first line of the block.
        if (first.equals("mermaid", ignoreCase = true)) {
            return lines.drop(firstIndex + 1).joinToString("\n").trimEnd().ifBlank { null }
        }

        if (!heuristics) return null
        return if (looksLikeMermaid(first)) text else null
    }

    /**
     * Text of a code block as Mermaid needs to see it.
     *
     * When the fence language is known - the bundled Mermaid plugin gives ```` ```mermaid ```` a
     * Language - the platform emits syntax-highlighted code whose line breaks are `<br>` elements,
     * which `wholeText()` drops. Losing them would hand Mermaid a single unparseable line.
     */
    private fun sourceOf(element: Element): String {
        val copy = element.clone()
        copy.select("br").forEach { it.replaceWith(TextNode("\n")) }
        return copy.wholeText().trim('\n', '\r').trimEnd()
    }

    /** `true` when [text] starts with a Mermaid diagram declaration. */
    fun looksLikeMermaid(text: String): Boolean {
        val head = text.trimStart().lowercase()
        return KEYWORDS.any { keyword ->
            head.startsWith(keyword) &&
                head.getOrNull(keyword.length)?.let { !it.isLetterOrDigit() && it != '_' } ?: true
        }
    }
}
