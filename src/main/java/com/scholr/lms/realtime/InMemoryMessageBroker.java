package com.scholr.lms.realtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * An in-process {@link MessageBroker} — the default the app wires up so a single node works out
 * of the box and the whole real-time tier is testable without external infrastructure. It models
 * the contract a Redis-pub/sub implementation must honor: publish reaches every current
 * subscriber of the topic, and closing a subscription stops delivery.
 *
 * <p>In production you would replace this bean with a {@code RedisMessageBroker} (or a managed
 * pub/sub) so fan-out crosses gateway nodes; nothing above {@link MessageBroker} changes. The
 * structure here — a concurrent map of topic to subscriber list — is deliberately the same
 * mental model as a broker's channel registry.
 */
@Component
public class InMemoryMessageBroker implements MessageBroker {

    private final Map<String, List<Consumer<String>>> topics = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, String message) {
        List<Consumer<String>> subscribers = topics.get(topic);
        if (subscribers == null) {
            return; // no node holds a connection for this room; nothing to fan out
        }
        for (Consumer<String> subscriber : subscribers) {
            subscriber.accept(message);
        }
    }

    @Override
    public Subscription subscribe(String topic, Consumer<String> onMessage) {
        topics.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(onMessage);
        return () -> {
            List<Consumer<String>> subscribers = topics.get(topic);
            if (subscribers != null) {
                subscribers.remove(onMessage);
                if (subscribers.isEmpty()) {
                    topics.remove(topic, subscribers);
                }
            }
        };
    }
}
