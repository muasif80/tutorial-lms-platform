package com.scholr.lms.web;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record CreateCourse(String title) {
    }

    public record CourseView(UUID id, String title, boolean published) {
    }

    private static CourseView toView(Course course) {
        return new CourseView(course.id(), course.title(), course.isPublished());
    }

    @PostMapping("/courses")
    public CourseView createCourse(@RequestBody CreateCourse request) {
        return toView(catalog.createCourse(request.title()));
    }

    @GetMapping("/courses")
    public List<CourseView> listCourses() {
        return catalog.allCourses().stream().map(CatalogController::toView).toList();
    }
}
