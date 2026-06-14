package com.scholr.lms.sync.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

/**
 * The server's authoritative, mergeable view of one learner's progress in one course — the convergence
 * point for offline edits coming from any number of devices. Tenant-scoped, with a {@code @Version}
 * optimistic lock.
 *
 * <p>Its two fields embody two classic conflict-resolution strategies, chosen so that the common cases need
 * no human arbitration:
 * <ul>
 *   <li><b>{@code completedLessons}</b> is a <em>grow-only set</em> (a G-Set CRDT). Merging is set union,
 *       which is commutative, associative, and idempotent — two devices that each completed different
 *       lessons offline both win, and re-syncing the same batch changes nothing. Completion is monotonic
 *       (you don't un-complete a lesson), so a grow-only set is exactly right and never conflicts.</li>
 *   <li><b>{@code lastPositionLesson}</b> is a <em>last-write-wins register</em>. There is only one "where
 *       was I," so when two devices disagree the most recent (by client timestamp) wins. This can lose the
 *       older device's cursor — an acceptable, well-defined resolution for a non-monotonic scalar, unlike
 *       silently overwriting the whole record.</li>
 * </ul>
 *
 * <p>Choosing data types that merge cleanly is the heart of offline-first design: most of the state is made
 * conflict-free by construction, and only the genuinely-single-valued cursor needs an arbitration rule.
 */
@Entity
@Table(
    name = "course_sync_state",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "learner_id", "course_id"})
)
public class CourseSyncState {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    /** Grow-only set of completed lesson ids — merged by union, conflict-free. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "course_sync_completed_lessons", joinColumns = @JoinColumn(name = "sync_state_id"))
    @Column(name = "lesson_id")
    private Set<UUID> completedLessons = new HashSet<>();

    /** Last-write-wins register for the learner's current position. */
    @Column(name = "last_position_lesson")
    private UUID lastPositionLesson;

    @Column(name = "last_position_at")
    private Instant lastPositionAt;

    @Version
    private long version;

    protected CourseSyncState() {
    }

    public CourseSyncState(UUID id, UUID learnerId, UUID courseId) {
        this.id = id;
        this.learnerId = learnerId;
        this.courseId = courseId;
    }

    public static CourseSyncState start(UUID learnerId, UUID courseId) {
        return new CourseSyncState(UUID.randomUUID(), learnerId, courseId);
    }

    /**
     * Merge an offline batch into this state using the conflict-free rules described above.
     * Idempotent: merging the same batch twice yields the same result.
     */
    public void merge(SyncBatch batch) {
        if (batch.completedLessons() != null) {
            completedLessons.addAll(batch.completedLessons()); // G-Set union
        }
        if (batch.lastPositionLesson() != null && batch.lastPositionAt() != null) {
            // last-write-wins: only advance the cursor if the batch's timestamp is newer
            if (lastPositionAt == null || batch.lastPositionAt().isAfter(lastPositionAt)) {
                lastPositionLesson = batch.lastPositionLesson();
                lastPositionAt = batch.lastPositionAt();
            }
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID learnerId() {
        return learnerId;
    }

    public UUID courseId() {
        return courseId;
    }

    public Set<UUID> completedLessons() {
        return Set.copyOf(completedLessons);
    }

    public int completedCount() {
        return completedLessons.size();
    }

    public UUID lastPositionLesson() {
        return lastPositionLesson;
    }

    public Instant lastPositionAt() {
        return lastPositionAt;
    }

    public long version() {
        return version;
    }
}
