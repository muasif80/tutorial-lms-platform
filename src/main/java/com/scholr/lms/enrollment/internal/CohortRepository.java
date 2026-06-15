package com.scholr.lms.enrollment.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.enrollment.domain.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRepository extends JpaRepository<Cohort, UUID> {

    /** The cohorts (classes) of a course — tenant-scoped. */
    List<Cohort> findByCourseId(UUID courseId);
}
