package com.scholr.lms.enrollment;

import java.util.UUID;

import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.Enrollment;
import com.scholr.lms.enrollment.internal.CohortRepository;
import com.scholr.lms.enrollment.internal.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the Enrollment context. */
@Service
public class EnrollmentService {

    private final CohortRepository cohorts;
    private final EnrollmentRepository enrollments;

    public EnrollmentService(CohortRepository cohorts, EnrollmentRepository enrollments) {
        this.cohorts = cohorts;
        this.enrollments = enrollments;
    }

    public Cohort createCohort(UUID courseId, int capacity) {
        return cohorts.save(Cohort.create(courseId, capacity));
    }

    /**
     * Enroll a learner — idempotent and atomic. A retried request returns the existing
     * enrollment instead of creating a duplicate; a new one loads the cohort, enforces
     * the seat invariant, and persists both the enrollment and the bumped seat count in
     * one transaction (with the cohort's @Version guarding against concurrent oversell).
     */
    @Transactional
    public Enrollment enroll(UUID cohortId, UUID learnerId) {
        return enrollments.findByCohortIdAndLearnerId(cohortId, learnerId)
            .orElseGet(() -> {
                Cohort cohort = cohorts.findById(cohortId)
                    .orElseThrow(() -> new IllegalArgumentException("cohort not found: " + cohortId));
                Enrollment enrollment = cohort.enroll(learnerId);
                cohorts.save(cohort);
                return enrollments.save(enrollment);
            });
    }
}
