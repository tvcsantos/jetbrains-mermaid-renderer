package com.github.tvcsantos.mermaidrender.render

/**
 * Turns Mermaid source into a PNG. The production implementation drives an offscreen JCEF browser;
 * tests substitute a fake so no browser is needed.
 */
interface MermaidImageRenderer {

    /** Blocking; must not be called on EDT or while holding a read lock. */
    fun render(request: DiagramRequest): RenderOutcome
}
