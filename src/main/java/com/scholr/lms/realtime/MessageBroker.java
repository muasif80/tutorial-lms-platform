package com.scholr.lms.realtime;

import java.util.function.Consumer;

/**
 * The fan-out seam for the real-time tier. A live class with tens of thousands of viewers can
 * never be served from a single WebSocket node — the gateway tier must stay <em>stateless</em>
 * and publish every message to a broker that fans it out to every node holding a connection in
 * that room. This interface is exactly that boundary.
 *
 * <p>The in-process {@link InMemoryMessageBroker} implements it for tests and single-node dev; in
 * production the same interface is backed by Redis pub/sub (or a managed pub/sub service). The
 * application code above this interface — rooms, presence, chat — does not change when you swap
 * the implementation, which is the whole point of isolating the fan-out behind one small port.
 */
public interface MessageBroker {

    /** Publish a message to everyone subscribed to {@code topic} (e.g. a live-class room id). */
    void publish(String topic, String message);

    /**
     * Subscribe to a topic. Returns a handle that unsubscribes when closed — a gateway node
     * subscribes on the first local connection to a room and unsubscribes when the last leaves,
     * so a node only receives traffic for rooms it actually serves.
     */
    Subscription subscribe(String topic, Consumer<String> onMessage);

    /** A live subscription; closing it stops delivery. */
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
