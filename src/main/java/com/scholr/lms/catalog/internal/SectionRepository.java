package com.scholr.lms.catalog.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.catalog.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findByLessonIdOrderByPositionAsc(UUID lessonId);

    long countByLessonId(UUID lessonId);
}
