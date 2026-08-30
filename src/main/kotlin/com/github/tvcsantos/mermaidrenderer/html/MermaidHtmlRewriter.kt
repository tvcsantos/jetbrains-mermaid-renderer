package com.github.tvcsantos.mermaidrenderer.html

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.github.tvcsantos.mermaidrenderer.render.CachedDiagram
import com.github.tvcsantos.mermaidrenderer.render.DiagramRequest
import com.github.tvcsantos.mermaidrenderer.render.DiagramState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Outcome of a rewrite. The counts exist so a silent no-op can be diagnosed, and [failed] lists the
 * diagrams that are currently broken, so the gutter marker can be kept in step.
 */
data class RewriteResult(
    val html: String,
    val candidates: Int,
    val matched: Int,
    val failed: Set<String> = emptySet(),
    /** Diagrams still being rendered; the popup waits on these to replace its content. */
    val pending: Int = 0,
)

/**
 * Replaces Mermaid code blocks in rendered documentation HTML with the rendered image.
 *
 * Pure and dependency-free: callers supply how a request is built and how it resolves, so tests
 * run without services or a browser.
 */
object MermaidHtmlRewriter {

    fun rewrite(
        html: String,
        heuristics: Boolean,
        requestFor: (String) -> DiagramRequest,
        resolve: (DiagramRequest) -> DiagramState,
        isTagged: (String) -> Boolean = { false },
        showProgress: Boolean = false,
    ): RewriteResult {
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)

        val candidates = LinkedHashSet<Element>()
        candidates.addAll(document.select("pre"))
        candidates.addAll(document.select(".mermaid"))

        var changed = false
        var matched = 0
        var pending = 0
        val failed = mutableSetOf<String>()
        for (element in candidates) {
            if (!element.hasParent()) continue
            val source = MermaidBlockDetector.mermaidSource(element, heuristics, isTagged) ?: continue
            matched++
            when (val state = resolve(requestFor(source))) {
                is DiagramState.Ready -> {
                    element.replaceWith(image(state.diagram))
                    changed = true
                }

                // Left exactly as the author wrote it. A Mermaid parse error runs for paragraphs
                // and would push the documentation off the screen, so it is reported by
                // MermaidErrorLineMarkerProvider in the gutter and written to the log instead.
                DiagramState.Failed -> failed += MermaidFences.normalize(source)

                // Silent by default: the block simply turns into the diagram once it is ready.
                DiagramState.Pending -> {
                    pending++
                    if (showProgress) {
                        element.after(note(MermaidBundle.message("diagram.pending")))
                        changed = true
                    }
                }
            }
        }

        return RewriteResult(
            html = if (changed) document.html() else html,
            candidates = candidates.size,
            matched = matched,
            failed = failed,
            pending = pending,
        )
    }

    private fun image(diagram: CachedDiagram): Element =
        Element("p").appendChild(
            Element("img")
                .attr("src", diagram.url)
                .attr("width", diagram.width.toString())
                .attr("height", diagram.height.toString())
                .attr("alt", MermaidBundle.message("diagram.alt"))
        )

    private fun note(text: String): Element =
        Element("p").appendChild(
            Element("i").text(text)
        )
}
