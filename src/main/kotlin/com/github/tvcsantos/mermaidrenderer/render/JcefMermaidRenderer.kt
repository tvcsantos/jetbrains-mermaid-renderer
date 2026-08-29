package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Renders Mermaid in an offscreen JCEF browser: mermaid.js produces SVG, the page draws it on a
 * canvas and hands back PNG bytes. The browser is created once and kept alive for the session.
 */
@Service(Service.Level.APP)
class JcefMermaidRenderer : Disposable {

    private val log = logger<JcefMermaidRenderer>()

    private val browserLock = ReentrantLock()

    /** mermaid.initialize() is global page state, so diagrams are rendered one at a time. */
    private val renderLock = Semaphore(1)

    private val pending = ConcurrentHashMap<String, CompletableFuture<RenderOutcome>>()

    @Volatile
    private var browser: JBCefBrowser? = null

    @Volatile
    private var pageLoaded = CountDownLatch(1)

    /** Blocking; must not be called on EDT or while holding a read lock. */
    fun render(request: DiagramRequest): RenderOutcome {
        if (!JBCefApp.isSupported()) {
            return RenderOutcome.Failure(MermaidBundle.message("jcef.unavailable"))
        }
        val timeout = MermaidSettings.getInstance().renderTimeoutSeconds.toLong()

        renderLock.acquire()
        try {
            val browser = obtainBrowser()
                ?: return RenderOutcome.Failure(MermaidBundle.message("jcef.unavailable"))
            if (!pageLoaded.await(timeout, TimeUnit.SECONDS)) {
                log.warn(
                    "The renderer page did not load in ${timeout}s " +
                        "(url=${browser.cefBrowser.url}, created=${browser.isCefBrowserCreated})"
                )
                return RenderOutcome.Failure(MermaidBundle.message("render.error.pageNotLoaded"))
            }

            val id = UUID.randomUUID().toString()
            val future = CompletableFuture<RenderOutcome>()
            pending[id] = future
            try {
                val script = buildString {
                    append("window.renderDiagram(")
                    append(js(id)).append(", ")
                    append(js(request.source)).append(", ")
                    append(js(request.theme)).append(", ")
                    append(request.scale).append(", ")
                    append(request.maxWidth).append(", ")
                    append(js(request.background))
                    append(");")
                }
                browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
                return future.get(timeout, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                return RenderOutcome.Failure(MermaidBundle.message("render.error.timeout", timeout))
            } catch (e: Exception) {
                log.warn("Mermaid rendering failed", e)
                return RenderOutcome.Failure(e.message ?: e.toString())
            } finally {
                pending.remove(id)
            }
        } finally {
            renderLock.release()
        }
    }

    private fun obtainBrowser(): JBCefBrowser? {
        browser?.let { return it }
        browserLock.withLock {
            browser?.let { return it }
            val created = AtomicReference<JBCefBrowser?>()
            ApplicationManager.getApplication().invokeAndWait({ created.set(createBrowser()) }, ModalityState.any())
            browser = created.get()
            return browser
        }
    }

    private fun createBrowser(): JBCefBrowser? {
        val url = MermaidResources.rendererUrl() ?: return null
        return try {
            val created = JBCefBrowser.createBuilder()
                .setOffScreenRendering(true)
                .build()
            Disposer.register(this, created)

            val query = JBCefJSQuery.create(created as JBCefBrowserBase)
            Disposer.register(created, query)
            query.addHandler { payload ->
                handleReply(payload)
                null
            }

            val latch = CountDownLatch(1)
            pageLoaded = latch
            created.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    cefBrowser ?: return
                    cefBrowser.executeJavaScript(
                        "window.__mermaidCallback = function(payload) { ${query.inject("payload")} };",
                        cefBrowser.url,
                        0,
                    )
                    latch.countDown()
                }

                override fun onLoadError(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    errorCode: CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?,
                ) {
                    log.warn("Cannot load the mermaid renderer page ($failedUrl): $errorCode $errorText")
                    latch.countDown()
                }
            }, created.cefBrowser)

            // The browser is never shown, so it has to be realized and sized explicitly -
            // otherwise nothing is ever loaded.
            created.component.setSize(BROWSER_WIDTH, BROWSER_HEIGHT)
            created.createImmediately()
            created.loadURL(url)
            created
        } catch (t: Throwable) {
            log.warn("Cannot start the offscreen browser used to render mermaid diagrams", t)
            null
        }
    }

    private fun handleReply(payload: String?) {
        val parts = payload?.split(SEPARATOR) ?: return
        val future = pending[parts.firstOrNull() ?: return] ?: return
        val outcome = try {
            if (parts.getOrNull(1) == "OK") {
                RenderOutcome.Success(
                    png = Base64.getDecoder().decode(parts[4]),
                    width = parts[2].toInt(),
                    height = parts[3].toInt(),
                )
            } else {
                RenderOutcome.Failure(
                    parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: MermaidBundle.message("render.error.unknown")
                )
            }
        } catch (e: Exception) {
            RenderOutcome.Failure(MermaidBundle.message("render.error.malformedResponse", e.message.orEmpty()))
        }
        future.complete(outcome)
    }

    private fun js(value: String): String = "\"" + StringUtil.escapeStringCharacters(value) + "\""

    override fun dispose() {
        val shutDown = RenderOutcome.Failure(MermaidBundle.message("render.error.shutDown"))
        pending.values.forEach { it.complete(shutDown) }
        pending.clear()
        browser = null
    }

    private companion object {
        const val SEPARATOR = '\u0001'

        /** Large enough that mermaid's layout is never constrained by the viewport. */
        const val BROWSER_WIDTH = 1600
        const val BROWSER_HEIGHT = 1200
    }
}
