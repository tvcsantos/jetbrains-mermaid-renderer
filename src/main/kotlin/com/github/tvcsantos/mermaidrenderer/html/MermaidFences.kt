package com.github.tvcsantos.mermaidrenderer.html

/**
 * Mermaid comment utilities for scanning raw doc-comment source.
 *
 * The rendered HTML cannot be trusted for this. When a Mermaid language plugin
 * is installed the platform syntax-highlights fences and can consume the info
 * string, so explicit tagging may be lost. Reading the source preserves the
 * original comment content and keeps fence detection reliable.
 */
object MermaidFences {

    private val MERMAID_FENCE = Regex(
        pattern = "^(`{3,}|~{3,})\\s*mermaid\\b.*$",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * The normalized bodies of every ```` ```mermaid ```` fence in
     * [commentText].
     *
     * @param commentText The comment source to scan.
     * @return The set of normalized diagram texts, empty when there are no
     * fences.
     */
    fun collectFencedBodies(commentText: String): Set<String> =
        CodeBlocks.collectFenceBodiesTo(
            commentText,
            opening = MERMAID_FENCE,
            destination = mutableSetOf()
        )
}
