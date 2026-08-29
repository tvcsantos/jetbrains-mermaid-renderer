package com.github.tvcsantos.mermaidrenderer.html

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

    /** Normalized bodies of every Mermaid fence in [commentText]. */
    fun taggedBodies(commentText: String): Set<String> {
        val bodies = mutableSetOf<String>()
        val body = StringBuilder()
        var fence: String? = null

        for (rawLine in commentText.lineSequence()) {
            val line = undecorate(rawLine)
            val open = fence
            if (open == null) {
                val match = FENCE_OPEN.find(line) ?: continue
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
     * Comparable form of a diagram: the rendered HTML and the comment source agree on the text but
     * not necessarily on indentation, so only line content is compared.
     */
    fun normalize(text: String): String =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

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
