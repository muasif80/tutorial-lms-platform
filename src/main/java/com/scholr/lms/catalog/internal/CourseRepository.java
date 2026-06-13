package com.scholr.lms.catalog.internal;

import java.util.UUID;

import com.scholr.lms.catalog.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
}
