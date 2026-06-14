package com.scholr.lms.assessment.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.assessment.domain.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    /** Tenant-scoped query: only the current tenant's assessments for a course. */
    List<Assessment> findByCourseId(UUID courseId);
}
