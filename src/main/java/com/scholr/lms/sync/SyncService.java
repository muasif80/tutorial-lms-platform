package com.scholr.lms.sync;

import java.util.UUID;

import com.scholr.lms.sync.domain.CourseSyncState;
import com.scholr.lms.sync.domain.SyncBatch;
import com.scholr.lms.sync.internal.CourseSyncStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server side of offline sync: accept a batch of changes a client made while disconnected and merge it
 * into the authoritative {@link CourseSyncState}. This is where the client's distributed-systems problem is
 * resolved — multiple devices, intermittent connectivity, and edits made against stale state, reconciled
 * without losing work.
 *
 * <p>The merge is <strong>idempotent and conflict-free for the common cases</strong> by construction (set
 * union for completed lessons, last-write-wins for the position cursor), so a client can safely retry a sync
 * it isn't sure landed — the same discipline that protected enrollment, submission, events, and payments,
 * now applied to a flaky mobile connection. A {@code @Version} optimistic lock detects the rarer case where
 * the server state changed concurrently; with conflict-free merge rules the natural response is simply to
 * re-read and re-merge, which converges.
 */
@Service
public class SyncService {

    private final CourseSyncStateRepository states;

    public SyncService(CourseSyncStateRepository states) {
        this.states = states;
    }

    /**
     * Merge an offline batch and return the converged server state. Safe to call repeatedly with the same
     * batch (idempotent) and safe to call from several devices (their batches union).
     */
    @Transactional
    public CourseSyncState sync(UUID learnerId, UUID courseId, SyncBatch batch) {
        CourseSyncState state = states.findByLearnerIdAndCourseId(learnerId, courseId)
            .orElseGet(() -> states.save(CourseSyncState.start(learnerId, courseId)));
        state.merge(batch);
        return states.save(state);
    }

    /** Read the current converged state for a learner's course (e.g. to hydrate a freshly-opened client). */
    @Transactional(readOnly = true)
    public CourseSyncState current(UUID learnerId, UUID courseId) {
        return states.findByLearnerIdAndCourseId(learnerId, courseId)
            .orElseGet(() -> CourseSyncState.start(learnerId, courseId));
    }
}
