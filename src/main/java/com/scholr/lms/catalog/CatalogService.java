package com.scholr.lms.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.catalog.domain.Lesson;
import com.scholr.lms.catalog.internal.CourseRepository;
import com.scholr.lms.catalog.internal.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the Catalog context. Every query here is tenant-scoped by Hibernate. */
@Service
public class CatalogService {

    private final CourseRepository courses;
    private final LessonRepository lessons;

    public CatalogService(CourseRepository courses, LessonRepository lessons) {
        this.courses = courses;
        this.lessons = lessons;
    }

    @Transactional
    public Course createCourse(String title) {
        return courses.save(Course.create(title));
    }

    /** Returns only the current tenant's courses — the tenant filter is automatic. */
    @Transactional(readOnly = true)
    public List<Course> allCourses() {
        return courses.findAll();
    }

    /** Part 13: the published courses for the student-facing catalogue (drafts excluded). */
    @Transactional(readOnly = true)
    public List<Course> publishedCourses() {
        return courses.findByPublishedTrueOrderByTitleAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Course> findCourse(UUID courseId) {
        return courses.findById(courseId);
    }

    /** Part 12: publish a course so learners can see it (draft → published). Idempotent. */
    @Transactional
    public Course publish(UUID courseId) {
        Course course = courses.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("course not found: " + courseId));
        course.publish();
        return courses.save(course);
    }

    /** Part 12: append a lesson to a course at the next position (the instructor authoring action). */
    @Transactional
    public Lesson addLesson(UUID courseId, String title, String body) {
        int next = (int) lessons.countByCourseId(courseId) + 1;
        return lessons.save(Lesson.of(courseId, title, next, body));
    }

    @Transactional(readOnly = true)
    public List<Lesson> lessons(UUID courseId) {
        return lessons.findByCourseIdOrderByPositionAsc(courseId);
    }

    @Transactional(readOnly = true)
    public long lessonCount(UUID courseId) {
        return lessons.countByCourseId(courseId);
    }
}
