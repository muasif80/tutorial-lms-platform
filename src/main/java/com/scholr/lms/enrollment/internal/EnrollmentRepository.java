package com.scholr.lms.enrollment.internal;

import com.scholr.lms.enrollment.domain.Enrollment;

/**
 * Module-private persistence detail. Nothing outside the enrollment module may
 * depend on this package — a rule enforced by ModularityTest (ArchUnit). A real
 * implementation backed by PostgreSQL arrives in Part 2.
 */
public interface EnrollmentRepository {

    void save(Enrollment enrollment);
}
