package com.scholr.lms.realtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One live class — chat, presence, and missed-message recovery — built on the {@link MessageBroker}
 * fan-out seam. A learner's WebSocket connection lives on some stateless gateway node; this object
 * models the room-level logic that node runs, independent of how many nodes share the room.
 *
 * <p>Two real-time guarantees are implemented here, both straight from the article:
 * <ul>
 *   <li><b>Presence at scale</b> — a concurrent set of present learner ids, joined/left as
 *       connections open and close. Presence is derived state, cheap to recompute, never the
 *       source of truth for anything billable.</li>
 *   <li><b>Reconnect without losing messages</b> — every published message gets a monotonic
 *       sequence number, and a short ring buffer of recent messages is retained. A client that
 *       drops and reconnects sends its last-seen sequence and {@link #replaySince} hands back only
 *       what it missed, in order — so a flaky connection produces a gap-free transcript, not a
 *       hole. (The buffer is bounded; older history comes from durable storage, not memory.)</li>
 * </ul>
 *
 * <p>This is intentionally transport-agnostic: it speaks in sequenced messages, not sockets, so it
 * is unit-testable and equally valid behind WebSockets, SSE, or a managed real-time service.
 */
public class LiveClassRoom {

    /** A fanned-out message with its room sequence number, so clients can detect and fill gaps. */
    public record SequencedMessage(long seq, String payload) {
    }

    private static final int BUFFER_SIZE = 256;

    private final String roomId;
    private final MessageBroker broker;
    private final Set<UUID> present = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong(0);

    // Bounded recent-message ring for reconnect recovery (newest appended, oldest evicted).
    private final List<SequencedMessage> recent = new ArrayList<>();

    public LiveClassRoom(String roomId, MessageBroker broker) {
        this.roomId = roomId;
        this.broker = broker;
    }

    public void join(UUID learnerId) {
        present.add(learnerId);
    }

    public void leave(UUID learnerId) {
        present.remove(learnerId);
    }

    public int presenceCount() {
        return present.size();
    }

    public boolean isPresent(UUID learnerId) {
        return present.contains(learnerId);
    }

    /**
     * Publish a chat/event message to the room. Assigns the next sequence number, retains it in
     * the bounded buffer for reconnect recovery, and fans it out through the broker to every
     * gateway node serving this room.
     *
     * @return the sequence number assigned to this message
     */
    public synchronized long publish(String payload) {
        long seq = sequence.incrementAndGet();
        recent.add(new SequencedMessage(seq, payload));
        if (recent.size() > BUFFER_SIZE) {
            recent.remove(0);
        }
        broker.publish(roomId, seq + ":" + payload);
        return seq;
    }

    /**
     * Everything published after {@code lastSeenSeq}, in order — what a reconnecting client missed.
     * If the gap is older than the retained buffer, the returned list starts at the oldest message
     * still in memory; the client learns it must reload the full transcript from durable storage.
     */
    public synchronized List<SequencedMessage> replaySince(long lastSeenSeq) {
        List<SequencedMessage> missed = new ArrayList<>();
        for (SequencedMessage m : recent) {
            if (m.seq() > lastSeenSeq) {
                missed.add(m);
            }
        }
        return missed;
    }

    public long currentSequence() {
        return sequence.get();
    }

    public String roomId() {
        return roomId;
    }
}
