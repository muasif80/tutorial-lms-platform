package com.scholr.lms.enrollment.internal;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.enrollment.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /** Tenant-scoped automatically by Hibernate @TenantId — backs idempotent enroll. */
    Optional<Enrollment> findByCohortIdAndLearnerId(UUID cohortId, UUID learnerId);
}
