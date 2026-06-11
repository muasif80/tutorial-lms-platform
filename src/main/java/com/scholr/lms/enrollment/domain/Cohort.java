package com.scholr.lms.enrollment.domain;

import com.scholr.lms.catalog.domain.CourseId;
import com.scholr.lms.shared.TenantId;

/**
 * The Cohort aggregate root. It protects the seat invariant — a cohort may never
 * be oversold beyond its capacity. Because the cohort and its seat count live in
 * one aggregate (and, from Part 2, one database transaction), enrollment is atomic:
 * no distributed lock, no race between two callers both believing the last seat is free.
 */
public class Cohort {

    private final CohortId id;
    private final TenantId tenant;
    private final CourseId course;
    private final int capacity;
    private int enrolledCount;

    public Cohort(CohortId id, TenantId tenant, CourseId course, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.id = id;
        this.tenant = tenant;
        this.course = course;
        this.capacity = capacity;
    }

    /** Enroll a learner, enforcing the seat invariant inside this aggregate. */
    public Enrollment enroll(LearnerId learner) {
        if (enrolledCount >= capacity) {
            throw new CohortFullException(id);
        }
        enrolledCount++;
        return new Enrollment(id, learner);
    }

    public CohortId id() {
        return id;
    }

    public TenantId tenant() {
        return tenant;
    }

    public CourseId course() {
        return course;
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
