package com.scholr.lms.catalog;

import java.util.List;

import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.catalog.internal.CourseRepository;
import org.springframework.stereotype.Service;

/** Public API of the Catalog context. Every query here is tenant-scoped by Hibernate. */
@Service
public class CatalogService {

    private final CourseRepository courses;

    public CatalogService(CourseRepository courses) {
        this.courses = courses;
    }

    public Course createCourse(String title) {
        return courses.save(Course.create(title));
    }

    /** Returns only the current tenant's courses — the tenant filter is automatic. */
    public List<Course> allCourses() {
        return courses.findAll();
    }
}
