package com.scholr.lms.assessment.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * The Assessment aggregate root: a quiz or exam attached to a course. Tenant-scoped.
 *
 * <p>It owns the policy a learner's attempts must obey — how many attempts are allowed and how
 * long each one may run — but it references its {@link Question}s <em>by id</em> (they are a
 * separate, paginatable bank), keeping the aggregate small. The policy here is the rulebook the
 * {@link com.scholr.lms.assessment.AssessmentService} enforces deterministically; none of it is
 * left to the client to honor.
 */
@Entity
@Table(name = "assessments")
public class Assessment {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false)
    private String title;

    /** How many attempts a learner may start. {@code 0} means unlimited. */
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    /** Wall-clock limit for a single attempt, in seconds. {@code 0} means untimed. */
    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    protected Assessment() {
    }

    public Assessment(UUID id, UUID courseId, String title, int maxAttempts, int timeLimitSeconds) {
        if (maxAttempts < 0 || timeLimitSeconds < 0) {
            throw new IllegalArgumentException("maxAttempts and timeLimitSeconds must be >= 0");
        }
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.maxAttempts = maxAttempts;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public static Assessment of(UUID courseId, String title, int maxAttempts, int timeLimitSeconds) {
        return new Assessment(UUID.randomUUID(), courseId, title, maxAttempts, timeLimitSeconds);
    }

    public boolean allowsAnotherAttempt(long alreadyStarted) {
        return maxAttempts == 0 || alreadyStarted < maxAttempts;
    }

    public boolean isTimed() {
        return timeLimitSeconds > 0;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID courseId() {
        return courseId;
    }

    public String title() {
        return title;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }
}
