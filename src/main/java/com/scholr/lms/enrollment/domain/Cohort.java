package com.scholr.lms.enrollment.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

/**
 * The Cohort aggregate root — now a persistent entity (Part 1 made it a pure
 * aggregate; Part 2 makes it durable). It still protects the seat invariant:
 * a cohort may never be oversold beyond its capacity.
 *
 * <p>Two things keep that invariant true even under concurrency: the in-aggregate
 * check in {@link #enroll}, and the {@code @Version} optimistic lock — if two
 * requests both read the last seat as free and try to save, one wins and the other
 * is rejected with an optimistic-lock failure (to retry), so the seat is never
 * double-sold. No distributed lock required, because the cohort and its seat count
 * live in one aggregate and one database transaction.
 */
@Entity
@Table(name = "cohorts")
public class Cohort {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Version
    private long version;

    protected Cohort() {
    }

    public Cohort(UUID id, UUID courseId, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.id = id;
        this.courseId = courseId;
        this.capacity = capacity;
        this.enrolledCount = 0;
    }

    public static Cohort create(UUID courseId, int capacity) {
        return new Cohort(UUID.randomUUID(), courseId, capacity);
    }

    /** Enroll a learner, enforcing the seat invariant inside this aggregate. */
    public Enrollment enroll(UUID learnerId) {
        if (enrolledCount >= capacity) {
            throw new CohortFullException(id);
        }
        enrolledCount++;
        return new Enrollment(UUID.randomUUID(), id, learnerId);
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

    public int capacity() {
        return capacity;
    }

    public int enrolledCount() {
        return enrolledCount;
    }

    public int seatsRemaining() {
        return capacity - enrolledCount;
    }
}
