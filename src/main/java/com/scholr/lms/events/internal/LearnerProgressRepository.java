package com.scholr.lms.events.internal;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.events.domain.LearnerProgress;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface LearnerProgressRepository extends JpaRepository<LearnerProgress, UUID> {

    /** The projection row for a learner in a course — find-or-create backs the upsert. */
    Optional<LearnerProgress> findByLearnerIdAndCourseId(UUID learnerId, UUID courseId);
}
