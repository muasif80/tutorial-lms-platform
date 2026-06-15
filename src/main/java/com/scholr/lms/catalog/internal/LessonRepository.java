package com.scholr.lms.catalog.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.catalog.domain.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    /** A course's lessons in author-defined order. */
    List<Lesson> findByCourseIdOrderByPositionAsc(UUID courseId);

    long countByCourseId(UUID courseId);
}
