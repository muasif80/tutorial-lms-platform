package com.scholr.lms.sync.internal;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.sync.domain.CourseSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface CourseSyncStateRepository extends JpaRepository<CourseSyncState, UUID> {

    /** The merge target for a (learner, course) — find-or-create backs the sync upsert. */
    Optional<CourseSyncState> findByLearnerIdAndCourseId(UUID learnerId, UUID courseId);
}
