package com.github.tvcsantos.mermaidrenderer.html

import org.jsoup.parser.Parser

/**
 * Finds ```` ```mermaid ```` fences in the *source* of a doc comment.
 *
 * The rendered HTML cannot be trusted for this: when a Mermaid language plugin is installed the
 * platform syntax-highlights the fence and the info string is consumed, so `language-mermaid` never
 * reaches the HTML and an explicitly tagged diagram looks exactly like an untagged one. Reading the
 * comment itself restores that intent, so tagging works even with the keyword heuristic switched
 * off.
 */
object MermaidFences {

    private val FENCE_OPEN = Regex("^(`{3,}|~{3,})\\s*mermaid\\b.*$", RegexOption.IGNORE_CASE)
    private val ANY_FENCE = Regex("^(`{3,}|~{3,}).*$")

    private val HTML_BLOCK = Regex(
        "<pre[^>]*class=[\"']([^\"']*)[\"'][^>]*>(.*?)</pre>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Every block in [commentText] that could be a diagram: any fence, plus HTML `<pre>` blocks.
     * Used to ask which of a comment's diagrams failed, where the tag is not the deciding factor.
     */
    fun candidateBodies(commentText: String): Set<String> =
        bodies(commentText, onlyMermaid = false) + htmlBodies(commentText)

    private fun htmlBodies(commentText: String): Set<String> {
        val undecorated = commentText.lineSequence().joinToString("\n") { undecorate(it) }
        return HTML_BLOCK.findAll(undecorated)
            .map { normalize(it.groupValues[2]) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Normalized bodies of every Mermaid fence in [commentText]. */
    fun taggedBodies(commentText: String): Set<String> = bodies(commentText, onlyMermaid = true)

    private fun bodies(commentText: String, onlyMermaid: Boolean): Set<String> {
        val bodies = mutableSetOf<String>()
        val body = StringBuilder()
        var fence: String? = null

        for (rawLine in commentText.lineSequence()) {
            val line = undecorate(rawLine)
            val open = fence
            if (open == null) {
                val match = (if (onlyMermaid) FENCE_OPEN else ANY_FENCE).find(line) ?: continue
                fence = match.groupValues[1]
                body.setLength(0)
            } else if (line.startsWith(open)) {
                normalize(body.toString()).takeIf { it.isNotEmpty() }?.let(bodies::add)
                fence = null
            } else {
                body.append(line).append('\n')
            }
        }

        return bodies
    }

    /**
     * Comparable form of a diagram.
     *
     * The two sides differ in more than indentation: text taken from rendered HTML has had its
     * entities decoded, while the comment source still reads `A --&gt;`. Both are decoded here so a
     * diagram recorded from one side can be found from the other.
     */
    fun normalize(text: String): String = Parser.unescapeEntities(text, false)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    /** Strips doc comment decoration: the opening and closing markers, a leading star, and `///`. */
    private fun undecorate(line: String): String {
        var text = line.trim()
        if (text.startsWith("/**")) text = text.removePrefix("/**")
        if (text.endsWith("*/")) text = text.removeSuffix("*/")
        text = text.trim()
        if (text.startsWith("///")) text = text.removePrefix("///")
        else if (text.startsWith("*")) text = text.removePrefix("*")
        return text.trim()
    }
}
