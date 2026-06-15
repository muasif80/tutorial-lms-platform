package com.scholr.lms.learning.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.learning.domain.LessonCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface LessonCompletionRepository extends JpaRepository<LessonCompletion, UUID> {

    List<LessonCompletion> findByLearnerIdAndCourseId(UUID learnerId, UUID courseId);

    boolean existsByLearnerIdAndLessonId(UUID learnerId, UUID lessonId);

    long countByLearnerIdAndCourseId(UUID learnerId, UUID courseId);
}
