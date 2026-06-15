package com.scholr.lms.learning.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * Learning &amp; Progress context (Part 13): the record that a learner has completed one lesson. Progress
 * is a fold over these facts — "lessons completed / total lessons" — so the read model is just a count,
 * never a number we have to keep in sync by hand. Tenant-scoped, and unique on
 * {@code (tenant_id, learner_id, lesson_id)} so marking the same lesson complete twice is idempotent:
 * the second write resolves to the existing row instead of double-counting progress.
 *
 * <p>The course id is denormalised onto the row so a learner's progress in a course is a single indexed
 * query, with no join back to the catalog — cross-context references stay by id, the series rule.
 */
@Entity
@Table(name = "lesson_completions")
public class LessonCompletion {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected LessonCompletion() {
    }

    public LessonCompletion(UUID id, UUID learnerId, UUID courseId, UUID lessonId, Instant completedAt) {
        this.id = id;
        this.learnerId = learnerId;
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.completedAt = completedAt;
    }

    public static LessonCompletion of(UUID learnerId, UUID courseId, UUID lessonId, Instant completedAt) {
        return new LessonCompletion(UUID.randomUUID(), learnerId, courseId, lessonId, completedAt);
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

    public UUID lessonId() {
        return lessonId;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
