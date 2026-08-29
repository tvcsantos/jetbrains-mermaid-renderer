package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Redraws the rendered doc comments of a file after a diagram finished rendering.
 *
 * The documentation itself has not changed - only this plugin's rewrite of it - so it is enough to
 * ask the renderers to rebuild their content, which re-reads [MermaidDocRenderItem.textToRender].
 * Requests are coalesced: a comment with several diagrams refreshes once.
 */
@Service(Service.Level.PROJECT)
class DocRenderRefresher(private val project: Project) : Disposable {

    private val scheduled = ConcurrentHashMap.newKeySet<VirtualFile>()

    /**
     * What was broken in each comment last time, so the daemon is only disturbed on a change.
     * Keyed per comment - a file usually holds several, and a file-level record would let one
     * comment overwrite another's and restart the daemon in a loop.
     */
    private val lastFailed = ConcurrentHashMap<Pair<VirtualFile, Int>, Set<String>>()

    fun scheduleRefresh(file: VirtualFile) {
        if (project.isDisposed || !scheduled.add(file)) return
        AppExecutorUtil.getAppScheduledExecutorService().schedule({
            scheduled.remove(file)
            refresh(file)
        }, COALESCE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Keeps [MermaidErrorLineMarkerProvider] in step. Its markers come from render outcomes, which
     * live in a service - the daemon cannot see them change, so a diagram that starts or stops
     * failing has to ask for a re-analysis. Only a real change triggers one, otherwise the restart
     * would re-run the doc render pass and loop.
     */
    fun syncMarkers(file: VirtualFile, commentOffset: Int, failed: Set<String>) {
        if (project.isDisposed) return
        val key = file to commentOffset
        val previous = if (failed.isEmpty()) lastFailed.remove(key) else lastFailed.put(key, failed)
        if (previous.orEmpty() == failed) return
        restartDaemon(file)
    }

    private fun restartDaemon(file: VirtualFile) {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            PsiManager.getInstance(project).findFile(file)
                ?.let { DaemonCodeAnalyzer.getInstance(project).restart(it) }
        }, ModalityState.any(), project.disposed)
    }

    private fun refresh(file: VirtualFile) {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            FileEditorManager.getInstance(project).getAllEditors(file)
                .filterIsInstance<TextEditor>()
                .forEach { DocRenderItemUpdater.updateRenderers(it.editor, true) }

        }, ModalityState.any(), project.disposed)
    }

    override fun dispose() {
        scheduled.clear()
        lastFailed.clear()
    }

    private companion object {
        const val COALESCE_DELAY_MS = 300L
    }
}
