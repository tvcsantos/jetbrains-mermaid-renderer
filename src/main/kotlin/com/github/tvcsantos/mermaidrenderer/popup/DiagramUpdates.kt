package com.github.tvcsantos.mermaidrenderer.popup

import com.github.tvcsantos.mermaidrenderer.html.RewriteResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Documentation that keeps up with the diagrams still being rendered.
 *
 * A popup is computed once and shown; unlike a rendered comment there is no
 * inlay to refresh when a diagram arrives a moment later. The platform's
 * answer is an updates flow whose emissions replace the browser content, so
 * this polls the rewrite until nothing is pending and emits whenever the
 * documentation actually changed. Collection is canceled by the platform when
 * the popup closes.
 */
internal object DiagramUpdates {

    private val POLL_INTERVAL_MS = 250L.milliseconds
    private val BUDGET_MS = 30_000L.milliseconds

    fun htmlUpdates(
        initialHtml: String,
        pollIntervalMs: Duration = POLL_INTERVAL_MS,
        budgetMs: Duration = BUDGET_MS,
        rewrite: () -> RewriteResult,
    ): Flow<String> = flow {
        var shown = initialHtml
        var waited = 0L.milliseconds

        while (waited < budgetMs) {
            delay(pollIntervalMs)
            waited += pollIntervalMs

            val result = rewrite()
            if (result.html != shown) {
                shown = result.html
                emit(shown)
            }
            // Nothing left to wait for: rendered,
            // or failed and reported in the gutter.
            if (result.pending == 0) return@flow
        }
    }
}
