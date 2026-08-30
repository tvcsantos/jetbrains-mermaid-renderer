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

    @Suppress("SpellCheckingInspection")
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
        // A keyword in its own right in Mermaid's grammar,
        // not a keyword followed by punctuation.
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
     * Returns the Mermaid source of [element], or `null` when it is an
     * ordinary code block.
     *
     * @param element The element to inspect.
     * @param heuristics When `true`, try to detect a Mermaid diagram from
     * the first line of the block, even when it is not tagged.
     * @param isTagged A predicate that returns `true` when a block's body was
     * fenced as `mermaid`.
     *
     * @return The Mermaid source, or `null` when [element] is not a Mermaid
     * block.
     */
    fun getMermaidSourceOrNull(
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
        return if (heuristics && looksLikeMermaid(first)) {
            text
        } else {
            null
        }
    }

    /**
     * Returns the element text in the form Mermaid expects.
     *
     * When a Mermaid fence is syntax-highlighted, the renderer may turn line
     * breaks into `<br>` elements. `wholeText()` restores those breaks to `\n`
     * so the detector can read the diagram source correctly.
     *
     * @param element The element to extract the text from.
     */
    private fun sourceOf(element: Element): String =
        element.wholeText().trim('\n', '\r').trimEnd()

    /**
     * Returns `true` when [text] starts with a Mermaid diagram keyword.
     *
     * The keyword must be followed by whitespace or end of line. This avoids
     * matching regular code such as `graph.addNode(x)` or `pie.slice(3)`.
     * Punctuated Mermaid forms like `stateDiagram-v2` and `gitGraph:` are
     * handled as explicit keywords.
     *
     * @param text The string to check for Mermaid diagram keywords.
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
