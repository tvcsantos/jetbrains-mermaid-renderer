package com.github.tvcsantos.mermaidrender.render

import com.intellij.codeInsight.documentation.render.DocRenderPassFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Recomputes the rendered doc comments of a file after a diagram finished rendering.
 *
 * The platform only recomputes them when the PSI changes, so the pass is driven manually here.
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
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@invokeLater
            FileEditorManager.getInstance(project).getAllEditors(file)
                .filterIsInstance<TextEditor>()
                .forEach { recalculate(it.editor, psiFile) }
        }, ModalityState.any(), project.disposed)
    }

    private fun recalculate(editor: Editor, psiFile: PsiFile) {
        ReadAction.nonBlocking<DocRenderPassFactory.Items> {
            DocRenderPassFactory.calculateItemsToRender(editor, psiFile)
        }
            .expireWith(this)
            .coalesceBy(this, editor)
            .finishOnUiThread(ModalityState.any()) { items ->
                if (!editor.isDisposed && !project.isDisposed) {
                    DocRenderPassFactory.applyItemsToRender(editor, project, items, false)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun dispose() {
        scheduled.clear()
    }

    private companion object {
        const val COALESCE_DELAY_MS = 300L
    }
}
