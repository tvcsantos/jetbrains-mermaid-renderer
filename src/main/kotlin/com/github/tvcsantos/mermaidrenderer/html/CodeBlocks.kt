package com.github.tvcsantos.mermaidrenderer.html

/**
 * Helpers for code block operations, including finding them and returning
 * their contents normalized.
 */
object CodeBlocks {

    private val ANY_FENCE = Regex(pattern = "^(`{3,}|~{3,}).*$")

    private val HTML_BLOCK = Regex(
        pattern = "<pre[^>]*class=[\"']([^\"']*)[\"'][^>]*>(.*?)</pre>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Every block in [commentText] that could be a diagram: any fence, plus
     * HTML `<pre>` blocks.
     *
     * Used to ask which of a comment's diagrams failed, where the tag is not
     * the deciding factor.
     *
     * @param commentText The comment source to scan.
     * @return The normalized bodies of every fence and HTML `<pre>` block in
     * [commentText].
     */
    fun candidateBodies(commentText: String): Set<String> =
        buildSet {
            collectFenceBodiesTo(
                commentText,
                opening = ANY_FENCE,
                destination = this
            )
            collectHtmlBodiesTo(
                commentText,
                destination = this
            )
        }

    /**
     * Collects normalized bodies of HTML `<pre>` blocks found in [commentText].
     *
     * Each line is stripped of comment markers before scanning, so they do not interfere with the
     * regex match. Only non-empty normalized bodies are added to [destination].
     *
     * @param commentText The comment source to scan.
     * @param destination The set to which normalized bodies are added.
     * @return [destination] for convenience.
     */
    private fun collectHtmlBodiesTo(
        commentText: String,
        destination: MutableSet<String>
    ): MutableSet<String> {
        val stripped = commentText.lineSequence()
            .joinToString("\n") {
                CommentMarkup.strip(it)
            }
        return HTML_BLOCK.findAll(stripped)
            .map { DiagramText.normalize(it.groupValues[2]) }
            .filter { it.isNotEmpty() }
            .toCollection(destination)
    }

    /**
     * Collects the body of every fence in [commentText] whose opening line
     * matches [opening].
     *
     * The marker that opened a fence is remembered, so a longer fence can hold
     * a shorter one without closing early. An unterminated fence contributes
     * nothing.
     *
     * @param commentText The comment source to scan.
     * @param opening A regex that matches the opening line of a fence, with
     * the marker in group 1.
     * @param destination The set to which normalized bodies are added.
     * @return [destination] for convenience.
     */
    fun collectFenceBodiesTo(
        commentText: String,
        opening: Regex,
        destination: MutableSet<String>
    ): MutableSet<String> {
        val body = StringBuilder()
        var fence: String? = null

        for (rawLine in commentText.lineSequence()) {
            val line = CommentMarkup.strip(rawLine)
            val open = fence
            if (open == null) {
                val match = opening.find(line) ?: continue
                fence = match.groupValues[1]
                body.clear()
            } else if (line.startsWith(open)) {
                DiagramText.normalize(body.toString()).takeIf {
                    it.isNotEmpty()
                }?.let(destination::add)
                fence = null
            } else {
                body.appendLine(line)
            }
        }

        return destination
    }
}
