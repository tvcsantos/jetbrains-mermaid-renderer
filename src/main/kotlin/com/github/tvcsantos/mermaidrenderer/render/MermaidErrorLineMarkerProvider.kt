package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.github.tvcsantos.mermaidrenderer.html.Fences
import com.github.tvcsantos.mermaidrenderer.html.MermaidFences
import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

/**
 * Marks a doc comment whose diagram Mermaid refused to parse.
 *
 * This is the plugin's *own* gutter icon, deliberately not the rendered-comment one: the platform
 * casts whatever `DocRenderItem.calcFoldingGutterIconRenderer` returns back to its own type, so
 * that icon can only ever be the platform's.
 *
 * The marker is anchored on the first token *after* the comment - the declaration it documents -
 * rather than on the comment itself. A rendered comment is folded into a custom fold region, and
 * the gutter of a folded line is not drawn, so a marker inside it is only visible in code mode.
 */
class MermaidErrorLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Line markers must be attached to leaf elements; this one anchors on the first token of
        // the documented declaration, which stays visible when the comment is folded away.
        if (!MermaidSettings.getInstance().showErrorMarker) return null
        if (element.firstChild != null) return null
        if (element is PsiWhiteSpace) return null
        // Tokens inside the comment would each match the same comment, and they are folded away
        // anyway when it is rendered.
        if (PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false) != null) return null
        val comment = precedingDocComment(element) ?: return null

        val text = comment.text
        if (!text.contains("```") && !text.contains("~~~") && !text.contains("mermaid", ignoreCase = true)) {
            return null
        }

        val service = service<MermaidRenderService>()
        val messages = Fences.candidateBodies(text)
            .mapNotNull { service.failureFor(it) }
            .distinct()
        if (messages.isEmpty()) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.General.Warning,
            { tooltip(messages) },
            { event, _ -> showDetails(event, messages) },
            GutterIconRenderer.Alignment.LEFT,
            { MermaidBundle.message("diagram.failed") },
        )
    }

    /** The doc comment this token is documented by, if the token directly follows one. */
    private fun precedingDocComment(element: PsiElement): PsiComment? {
        var previous = PsiTreeUtil.prevLeaf(element, true)
        while (previous is PsiWhiteSpace) previous = PsiTreeUtil.prevLeaf(previous, true)
        return PsiTreeUtil.getParentOfType(previous, PsiComment::class.java, false)
    }

    private fun tooltip(messages: List<String>): String =
        "<html><b>" + MermaidBundle.message("diagram.failed") + "</b><br><br>" + asHtml(messages) + "</html>"

    private fun showDetails(event: MouseEvent?, messages: List<String>) {
        val where = event?.let { RelativePoint(it) } ?: return
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(asHtml(messages), MessageType.WARNING, null)
            .setHideOnClickOutside(true)
            .setHideOnKeyOutside(true)
            .createBalloon()
            .show(where, Balloon.Position.atRight)
    }

    private fun asHtml(messages: List<String>): String = DiagramErrorText.toHtml(messages)
}
