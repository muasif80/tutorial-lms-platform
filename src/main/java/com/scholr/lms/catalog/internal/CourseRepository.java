package com.scholr.lms.catalog.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.catalog.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    /** Part 13: the published catalogue a learner can browse — drafts stay hidden. Tenant-scoped. */
    List<Course> findByPublishedTrueOrderByTitleAsc();
}
