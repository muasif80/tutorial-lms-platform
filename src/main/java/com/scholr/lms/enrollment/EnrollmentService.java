package com.scholr.lms.enrollment;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.Enrollment;
import com.scholr.lms.enrollment.internal.CohortRepository;
import com.scholr.lms.enrollment.internal.EnrollmentRepository;
import com.scholr.lms.events.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the Enrollment context. */
@Service
public class EnrollmentService {

    private final CohortRepository cohorts;
    private final EnrollmentRepository enrollments;
    private final OutboxWriter outbox;

    public EnrollmentService(CohortRepository cohorts, EnrollmentRepository enrollments, OutboxWriter outbox) {
        this.cohorts = cohorts;
        this.enrollments = enrollments;
        this.outbox = outbox;
    }

    public Cohort createCohort(UUID courseId, int capacity) {
        return cohorts.save(Cohort.create(courseId, capacity));
    }

    /**
     * Enroll a learner — idempotent and atomic. A retried request returns the existing
     * enrollment instead of creating a duplicate; a new one loads the cohort, enforces
     * the seat invariant, and persists both the enrollment and the bumped seat count in
     * one transaction (with the cohort's @Version guarding against concurrent oversell).
     *
     * <p>Part 5: on a <em>new</em> enrollment we also write an {@code enrollment.created} event to
     * the transactional outbox, in this same transaction. The enrollment row, the seat bump, and the
     * event therefore commit together or not at all — closing the dual-write gap so the analytics
     * pipeline downstream can never disagree with the system of record. The event is not written on
     * the idempotent retry path, so a duplicated request never emits a duplicate event either.
     */
    @Transactional
    public Enrollment enroll(UUID cohortId, UUID learnerId) {
        return enrollments.findByCohortIdAndLearnerId(cohortId, learnerId)
            .orElseGet(() -> {
                Cohort cohort = cohorts.findById(cohortId)
                    .orElseThrow(() -> new IllegalArgumentException("cohort not found: " + cohortId));
                Enrollment enrollment = cohort.enroll(learnerId);
                cohorts.save(cohort);
                Enrollment saved = enrollments.save(enrollment);
                outbox.append("enrollment.created", saved.id(), learnerId + ":" + cohort.courseId());
                return saved;
            });
    }

    /** Part 12: the cohorts (classes) of a course — tenant-scoped. */
    @Transactional(readOnly = true)
    public List<Cohort> cohortsForCourse(UUID courseId) {
        return cohorts.findByCourseId(courseId);
    }

    /** Part 12: the enrollments (roster) of a cohort. */
    @Transactional(readOnly = true)
    public List<Enrollment> rosterForCohort(UUID cohortId) {
        return enrollments.findByCohortId(cohortId);
    }

    /** Part 13: a learner's enrollments — backs the student's "my courses". */
    @Transactional(readOnly = true)
    public List<Enrollment> enrollmentsForLearner(UUID learnerId) {
        return enrollments.findByLearnerId(learnerId);
    }

    @Transactional(readOnly = true)
    public Cohort cohort(UUID cohortId) {
        return cohorts.findById(cohortId).orElseThrow(() -> new IllegalArgumentException("cohort not found: " + cohortId));
    }
}
