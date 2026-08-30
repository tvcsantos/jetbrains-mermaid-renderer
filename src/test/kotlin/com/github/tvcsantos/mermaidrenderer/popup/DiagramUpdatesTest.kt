package com.github.tvcsantos.mermaidrenderer.popup

import com.github.tvcsantos.mermaidrenderer.html.RewriteResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class DiagramUpdatesTest {

    private val pendingResult = RewriteResult("<p>code block</p>", candidates = 1, matched = 1, pending = 1)
    private val readyResult = RewriteResult("<p><img src='diagram.png'></p>", candidates = 1, matched = 1)

    @Test
    fun `emits the documentation once the diagram is ready, then completes`() = runBlocking {
        var polls = 0
        val updates = DiagramUpdates.htmlUpdates(pendingResult.html, pollIntervalMs = 1.milliseconds, budgetMs = 1_000.milliseconds) {
            polls++
            if (polls < 3) pendingResult else readyResult
        }

        assertEquals(listOf(readyResult.html), updates.toList())
    }

    @Test
    fun `says nothing while the documentation has not changed`() = runBlocking {
        val updates = DiagramUpdates.htmlUpdates(pendingResult.html, pollIntervalMs = 1.milliseconds, budgetMs = 20.milliseconds) {
            pendingResult
        }

        assertTrue("A popup must not be redrawn with identical content", updates.toList().isEmpty())
    }

    @Test
    fun `gives up when the diagram never arrives`() = runBlocking {
        var polls = 0
        val updates = DiagramUpdates.htmlUpdates(pendingResult.html, pollIntervalMs = 1.milliseconds, budgetMs = 20.milliseconds) {
            polls++
            pendingResult
        }

        updates.toList()

        assertTrue("The flow must end rather than poll forever", polls in 1..40)
    }

    @Test
    fun `a diagram that fails stops the waiting`() = runBlocking {
        var polls = 0
        val failed = RewriteResult("<p>code block</p>", candidates = 1, matched = 1, failed = setOf("graph TD;"))

        DiagramUpdates.htmlUpdates(pendingResult.html, pollIntervalMs = 1.milliseconds, budgetMs = 1_000.milliseconds) {
            polls++
            failed
        }.toList()

        assertEquals("Nothing is pending any more, so the flow should stop at once", 1, polls)
    }
}
