package com.scholr.lms.assessment.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

/**
 * One learner's attempt at an assessment — the aggregate where correctness meets concurrency.
 * Tenant-scoped.
 *
 * <p>Two independent mechanisms make submission <em>exactly-once</em>:
 * <ul>
 *   <li>A per-tenant unique {@code (assessment_id, learner_id, attempt_no)} constraint, so a
 *       retried "start" resolves to the same attempt rather than spawning duplicates.</li>
 *   <li>A {@code @Version} optimistic lock plus a one-way {@code IN_PROGRESS → SUBMITTED}
 *       transition guarded by {@link #submit}: the first submit wins and records the score;
 *       a duplicate submit (double-click, network retry, queue redelivery) is a no-op that
 *       returns the score already recorded — never a second grade, never a changed grade.</li>
 * </ul>
 *
 * <p>The attempt stores the <em>graded result</em> (score and max), not the raw answers — those
 * are persisted as the learner saves, off this aggregate. The attempt is the small, hot row that
 * the submit transaction locks; keeping it small is what keeps that transaction cheap.
 */
@Entity
@Table(
    name = "attempts",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"tenant_id", "assessment_id", "learner_id", "attempt_no"})
)
public class Attempt {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    /** 1-based attempt number for this learner on this assessment. */
    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** The deadline (started + time limit) for a timed assessment; null if untimed. */
    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "score")
    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Version
    private long version;

    protected Attempt() {
    }

    public Attempt(UUID id, UUID assessmentId, UUID learnerId, int attemptNo,
                   Instant startedAt, Instant deadlineAt) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.learnerId = learnerId;
        this.attemptNo = attemptNo;
        this.status = AttemptStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
    }

    public static Attempt start(UUID assessmentId, UUID learnerId, int attemptNo,
                                Instant startedAt, Instant deadlineAt) {
        return new Attempt(UUID.randomUUID(), assessmentId, learnerId, attemptNo, startedAt, deadlineAt);
    }

    /** True once this attempt has been graded (whether submitted or expired). */
    public boolean isGraded() {
        return status == AttemptStatus.SUBMITTED || status == AttemptStatus.EXPIRED;
    }

    public boolean isExpiredAt(Instant now) {
        return deadlineAt != null && now.isAfter(deadlineAt);
    }

    /**
     * Record the grade and close the attempt. One-way and idempotent: if the attempt is already
     * graded this does nothing, so a duplicate submission cannot overwrite the first result.
     *
     * @return {@code true} if this call actually graded the attempt; {@code false} if it was
     *         already graded (the caller should return the existing result).
     */
    public boolean submit(int score, int maxScore, Instant submittedAt, boolean expired) {
        if (isGraded()) {
            return false;
        }
        this.score = score;
        this.maxScore = maxScore;
        this.submittedAt = submittedAt;
        this.status = expired ? AttemptStatus.EXPIRED : AttemptStatus.SUBMITTED;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID assessmentId() {
        return assessmentId;
    }

    public UUID learnerId() {
        return learnerId;
    }

    public int attemptNo() {
        return attemptNo;
    }

    public AttemptStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant deadlineAt() {
        return deadlineAt;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Integer score() {
        return score;
    }

    public Integer maxScore() {
        return maxScore;
    }
}
