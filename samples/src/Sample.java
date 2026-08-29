/**
 * Sample Javadoc with a tagged Mermaid block.
 *
 * <pre class="mermaid">
 * graph TD;
 *   Request --&gt; Router;
 *   Router --&gt; Handler;
 *   Handler --&gt; Response;
 * </pre>
 *
 * And an untagged one, picked up by the heuristic:
 *
 * <pre>
 * sequenceDiagram
 *   Client-&gt;&gt;Server: GET /orders
 *   Server--&gt;&gt;Client: 200 OK
 * </pre>
 *
 * A broken diagram, which should show an error note instead of an image:
 *
 * <pre class="mermaid">
 * graph TD;
 *   A --&gt;
 * </pre>
 */
public class Sample {

    /**
     * Ordinary code blocks stay untouched:
     *
     * <pre>{@code
     * var sample = new Sample();
     * }</pre>
     */
    public void method() {
    }
}
