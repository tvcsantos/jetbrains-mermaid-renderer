package com.github.tvcsantos.mermaidrenderer.html

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.github.tvcsantos.mermaidrenderer.render.CachedDiagram
import com.github.tvcsantos.mermaidrenderer.render.DiagramRequest
import com.github.tvcsantos.mermaidrenderer.render.DiagramState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * The outcome of a rewrite: the resulting HTML, and what the rewrite found
 * while producing it.
 */
data class RewriteResult(
    /** The rewritten HTML, or the original if nothing changed */
    val html: String,
    /** Code blocks that were candidates for Mermaid detection */
    val candidates: Int,
    /** Diagrams that were detected as Mermaid code blocks */
    val matched: Int,
    /** Diagrams that failed to render, normalized for comparison */
    val failed: Set<String> = emptySet(),
    /** Diagrams still being rendered */
    val pending: Int = 0,
)

/**
 * Helpers for rewriting documentation HTML, including replacing Mermaid code
 * blocks with their rendered image.
 */
object MermaidHtmlRewriter {

    private data class Findings(
        var changed: Boolean = false,
        var matched: Int = 0,
        var pending: Int = 0,
        val failed: MutableSet<String> = mutableSetOf(),
    )

    /**
     * Rewrites [html], replacing every Mermaid code block that has a rendered
     * image with that image.
     *
     * A block whose diagram is not ready is left as the author wrote it, and
     * so is one that failed; both are reported in the result. When nothing is
     * replaced, [html] is returned unchanged rather than reserialized.
     *
     * @param html The documentation HTML to rewrite.
     * @param heuristics Whether an untagged block may be guessed to be a
     * diagram from its first line.
     * @param requestFor Builds the render request for a diagram's source.
     * @param resolve Answers whether a requested diagram is ready, pending or
     * failed.
     * @param isTagged Tells whether a block's body was fenced as `mermaid` in
     * the comment source.
     * @param showProgress Whether a pending diagram is announced in the
     * documentation.
     * @return The rewritten HTML and what the rewrite found.
     */
    fun rewrite(
        html: String,
        heuristics: Boolean,
        requestFor: (String) -> DiagramRequest,
        resolve: (DiagramRequest) -> DiagramState,
        isTagged: (String) -> Boolean = { false },
        showProgress: Boolean = false,
    ): RewriteResult {
        val document = Jsoup.parse(html).apply {
            outputSettings().prettyPrint(false)
        }

        val candidates = buildSet {
            addAll(document.select("pre"))
            addAll(document.select(".mermaid"))
        }

        val findings = Findings()
        candidates.forEach { element ->
            rewriteBlock(
                element,
                heuristics,
                isTagged,
                resolve,
                requestFor,
                showProgress,
                findings,
            )
        }

        return RewriteResult(
            html = if (findings.changed) document.html() else html,
            candidates = candidates.size,
            matched = findings.matched,
            failed = findings.failed,
            pending = findings.pending,
        )
    }

    private fun rewriteBlock(
        element: Element,
        heuristics: Boolean,
        isTagged: (String) -> Boolean,
        resolve: (DiagramRequest) -> DiagramState,
        requestFor: (String) -> DiagramRequest,
        showProgress: Boolean,
        findings: Findings,
    ) {
        if (!element.hasParent()) return

        val source = MermaidBlockDetector.getMermaidSourceOrNull(
            element,
            heuristics,
            isTagged
        ) ?: return

        findings.matched++

        when (val state = resolve(requestFor(source))) {
            is DiagramState.Ready -> {
                element.replaceWith(state.diagram.image)
                findings.changed = true
            }

            // Left exactly as the author wrote it. A Mermaid parse error
            // runs for paragraphs and would push the documentation off the
            // screen, so it is reported by MermaidErrorLineMarkerProvider
            // in the gutter and written to the log instead.
            DiagramState.Failed -> {
                findings.failed += DiagramText.normalize(source)
            }

            // Silent by default: the block simply turns into the diagram
            // once it is ready.
            DiagramState.Pending -> {
                findings.pending++
                if (showProgress) {
                    element.after(
                        MermaidBundle.message(
                            key = "diagram.pending"
                        ).toNote()
                    )
                    findings.changed = true
                }
            }
        }
    }

    private val CachedDiagram.image get() =
        Element("p").appendChild(
            Element("img")
                .attr("src", url)
                .attr("width", width.toString())
                .attr("height", height.toString())
                .attr("alt",
                    MermaidBundle.message("diagram.alt")
                )
        )

    private fun String.toNote(): Element =
        Element("p").appendChild(
            Element("i").text(this)
        )
}