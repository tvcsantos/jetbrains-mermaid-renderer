/**
 * Sample KDoc with a tagged Mermaid fence.
 *
 * ```mermaid
 * stateDiagram-v2
 *     [*] --> Draft
 *     Draft --> Placed
 *     Placed --> Shipped
 *     Shipped --> [*]
 * ```
 *
 * An untagged fence, picked up by the heuristic:
 *
 * ```
 * flowchart LR
 *     A[Parse] --> B[Render]
 *     B --> C[Cache]
 * ```
 *
 * And an ordinary code block, which must stay as code:
 *
 * ```kotlin
 * val order = Order()
 * ```
 */
class Order
