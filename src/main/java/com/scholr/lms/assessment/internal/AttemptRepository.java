package com.scholr.lms.assessment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.assessment.domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    /** All of a learner's attempts at an assessment — tenant-scoped, used to enforce the attempts policy. */
    List<Attempt> findByAssessmentIdAndLearnerId(UUID assessmentId, UUID learnerId);

    /** A specific attempt by its natural key — the seam for idempotent start/submit. */
    Optional<Attempt> findByAssessmentIdAndLearnerIdAndAttemptNo(UUID assessmentId, UUID learnerId, int attemptNo);
}
