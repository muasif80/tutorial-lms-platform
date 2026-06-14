package com.scholr.lms.sync.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A batch of changes a client collected <em>while offline</em> and is now pushing to the server to merge.
 * This is the payload of the client-side distributed-systems problem: a learner made progress on a plane,
 * a second device made different progress, and now both must reconcile against the server without losing
 * work and without a destructive last-writer-wins overwrite of everything.
 *
 * <p>The batch is deliberately shaped so it can be merged by <em>conflict-free</em> rules rather than
 * arbitration: the set of completed lessons merges by union (order- and duplicate-independent), and the
 * single "where was I" cursor carries a timestamp so the latest position wins. {@code baseVersion} is the
 * server version the client last saw, used to detect that the server moved underneath it.
 *
 * @param completedLessons   lessons the client marked complete while offline (a grow-only set)
 * @param lastPositionLesson the lesson the learner was last on, or {@code null} if unchanged
 * @param lastPositionAt     the client-clock time of that position (drives last-write-wins)
 * @param baseVersion        the server state version the client started from
 */
public record SyncBatch(
    Set<UUID> completedLessons,
    UUID lastPositionLesson,
    Instant lastPositionAt,
    long baseVersion
) {
}
