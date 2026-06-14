package com.scholr.lms.assessment.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.assessment.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /** The question bank for an assessment — tenant-scoped. */
    List<Question> findByAssessmentId(UUID assessmentId);
}
