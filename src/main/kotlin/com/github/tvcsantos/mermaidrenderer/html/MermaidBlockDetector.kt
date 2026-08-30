package com.github.tvcsantos.mermaidrenderer.html

import org.jsoup.nodes.Element

/**
 * Decides whether a rendered code block holds a Mermaid diagram.
 *
 * Doc comment renderers differ in what survives into the HTML:
 *
 * - KDoc keeps the fence info string as a `language-mermaid` class.
 * - Javadoc authors write `<pre class="mermaid">`, and some renderers drop the
 *   marker entirely. For those, either we get the information from the comment
 *   source, or we have to guess from the first line of the block, hence the
 *   optional keyword heuristic.
 */
object MermaidBlockDetector {

    private const val MERMAID_MARKER = "mermaid"

    private val KEYWORDS = listOf(
        "graph",
        "flowchart",
        "flowchart-elk",
        "sequencediagram",
        "classdiagram",
        "statediagram",
        "statediagram-v2",
        "erdiagram",
        "journey",
        "gantt",
        "pie",
        "gitgraph",
        // A keyword in its own right in Mermaid's grammar, not a keyword followed by punctuation.
        "gitgraph:",
        "mindmap",
        "timeline",
        "quadrantchart",
        "requirementdiagram",
        "c4context",
        "c4container",
        "c4component",
        "c4dynamic",
        "c4deployment",
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

    /**
     * Mermaid source of [element], or `null` when it is an ordinary code
     * block.
     *
     * [isTagged] is asked whether a block's body was fenced as
     * ```` ```mermaid ```` in the comment source - the only reliable marker
     * once syntax highlighting has consumed the info string.
     */
    fun mermaidSource(
        element: Element,
        heuristics: Boolean,
        isTagged: (String) -> Boolean = { false },
    ): String? {
        val code = element.selectFirst("code")
        val text = sourceOf(code ?: element)

        if (text.isBlank()) return null

        val classes = (element.className() + " " + code?.className().orEmpty()).lowercase()

        if (classes.contains(MERMAID_MARKER)) return text

        if (isTagged(MermaidFences.normalize(text))) return text

        val lines = text.lines()

        // Get the first non-blank line, which is either the
        // fence info string or the first line of the block.
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }

        if (firstIndex < 0) return null

        val first = lines[firstIndex].trim()

        // Some renderers keep the fence info string as the
        // first line of the block.
        if (first.equals(MERMAID_MARKER, ignoreCase = true)) {
            return lines.drop(firstIndex + 1)
                .joinToString("\n")
                .trimEnd()
                .ifBlank { null }
        }

        // Nothing found so far. If heuristics are enabled
        // try to detect a Mermaid diagram from the first
        // line of the block.
        return if (heuristics && looksLikeMermaid(first)) text else null
    }

    /**
     * Text of a code block as Mermaid needs to see it.
     *
     * When the fence language is known - the bundled Mermaid plugin gives ```` ```mermaid ```` a
     * Language - the platform emits syntax-highlighted code whose line breaks are `<br>` elements.
     * jsoup's `wholeText()` turns those back into newlines, which the detector tests pin down.
     */
    private fun sourceOf(element: Element): String =
        element.wholeText().trim('\n', '\r').trimEnd()

    /**
     * `true` when [text] starts with a Mermaid diagram declaration.
     *
     * The keyword has to *end* there - at end of line or whitespace, which is what Mermaid's own
     * lexer requires (`stateDiagram\s+`). Accepting any non-identifier character would be enough for
     * `graph TD`, but it also swallows ordinary code and configuration: `graph.addNode(x)`,
     * `pie.slice(3)` and a YAML `graph: mygraph` would all be sent to Mermaid and reported as broken
     * diagrams. Forms that genuinely carry punctuation - `stateDiagram-v2`, `gitGraph:` - are
     * keywords of their own, so they match while an invented `stateDiagram-v3` does not.
     */
    fun looksLikeMermaid(text: String): Boolean {
        val head = text.trimStart().lowercase()
        return KEYWORDS.any { keyword ->
            head.startsWith(keyword) &&
                    head.getOrNull(keyword.length).let {
                        it == null || it.isWhitespace()
                    }
        }
    }
}
