package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

    fun scheduleRefresh(file: VirtualFile) {
        if (project.isDisposed || !scheduled.add(file)) return
        AppExecutorUtil.getAppScheduledExecutorService().schedule({
            scheduled.remove(file)
            refresh(file)
        }, COALESCE_DELAY_MS, TimeUnit.MILLISECONDS)
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
    }

    private companion object {
        const val COALESCE_DELAY_MS = 300L
    }
}
