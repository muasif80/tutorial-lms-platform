package com.scholr.lms.events.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/**
 * A read model: one learner's progress in one course, <em>derived</em> from the event stream rather
 * than written directly by the business services. Tenant-scoped.
 *
 * <p>This is the payoff of the whole pipeline. Progress, analytics, and dashboards are projections
 * built by consuming events — they are <strong>eventually consistent</strong> with the source
 * aggregates, never the source of truth. Because the consumer that maintains this row is idempotent
 * (see {@link ProcessedEvent}), replaying the stream or redelivering an event reproduces exactly the
 * same numbers; the projection can always be rebuilt from scratch by replaying history.
 *
 * <p>The unique {@code (tenant_id, learner_id, course_id)} key makes the projection a simple
 * find-or-create upsert, so a consumer that processes the same lesson-completed event twice (before
 * its dedup record committed, say) still can't double-count — defense in depth behind the dedup table.
 */
@Entity
@Table(
    name = "learner_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "learner_id", "course_id"})
)
public class LearnerProgress {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "lessons_completed", nullable = false)
    private int lessonsCompleted;

    @Column(name = "enrolled", nullable = false)
    private boolean enrolled;

    protected LearnerProgress() {
    }

    public LearnerProgress(UUID id, UUID learnerId, UUID courseId) {
        this.id = id;
        this.learnerId = learnerId;
        this.courseId = courseId;
        this.lessonsCompleted = 0;
        this.enrolled = false;
    }

    public static LearnerProgress start(UUID learnerId, UUID courseId) {
        return new LearnerProgress(UUID.randomUUID(), learnerId, courseId);
    }

    public void markEnrolled() {
        this.enrolled = true;
    }

    public void incrementLessonsCompleted() {
        this.lessonsCompleted++;
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

    public int lessonsCompleted() {
        return lessonsCompleted;
    }

    public boolean isEnrolled() {
        return enrolled;
    }
}
