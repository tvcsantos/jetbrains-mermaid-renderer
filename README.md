# Mermaid Renderer

An IntelliJ plugin that renders [Mermaid](https://mermaid.js.org) diagrams inside the IDE's
**Rendered Documentation Comments**.

Write a diagram in a KDoc or Javadoc comment:

````kotlin
/**
 * Order lifecycle.
 *
 * ```mermaid
 * stateDiagram-v2
 *     [*] --> Draft
 *     Draft --> Placed
 *     Placed --> Shipped
 *     Shipped --> [*]
 * ```
 */
class Order
````

Toggle the rendered view (the gutter icon, or <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>Q</kbd>) and the
code block is replaced by the diagram.

## How it works

- The plugin wraps the documentation produced by every other `InlineDocumentationProvider`, so no
  language-specific code is needed: any language whose rendered docs contain a Mermaid block works.
  The platform builds a rendered comment two ways and each needs the opposite registration order -
  the render pass keeps one item per text range and the **last** provider wins (`order="last"`),
  while toggling a single comment from the gutter resolves `findInlineDocumentation` with the
  **first** non-null answer (`order="first"`). Hence two extensions.
- Diagrams are rendered locally: a bundled `mermaid.min.js` runs in an offscreen JCEF browser,
  which draws the resulting SVG onto a canvas and returns PNG bytes. **No network access and no
  external tools.**
- Rendered PNGs are cached on disk, so reopening a file shows diagrams instantly.
- `renderText()` runs under a read lock, so rendering never blocks it: the first pass shows a
  "Rendering diagram..." note and the comment is refreshed as soon as the image is ready.

## Detection

A code block is treated as Mermaid when it is tagged - ` ```mermaid ` in KDoc or markdown Javadoc,
`<pre class="mermaid">` in HTML Javadoc - or, when the heuristic is enabled (default), when it
starts with a Mermaid keyword such as `graph`, `flowchart`, `sequenceDiagram`, `classDiagram`,
`stateDiagram`, `erDiagram`, `gantt` or `mindmap`.

## Settings

**Settings | Tools | Mermaid Renderer**: heuristic detection, diagram theme (follows the IDE theme by
default), maximum width, render timeout, disk cache limit, and a button to clear the cache.

## Building

```bash
./gradlew build          # compile, test, verify
./gradlew runIde         # sandbox IDE with the plugin installed
./gradlew buildPlugin    # distributable ZIP in build/distributions
```

Requires JDK 21. The plugin targets IntelliJ IDEA 2026.2 (build 262) and newer.

## Licensing

The plugin bundles `mermaid.min.js` (MIT); see
`src/main/resources/mermaid/LICENSE-mermaid.txt`.
