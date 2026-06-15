package com.scholr.lms.web.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.scholr.lms.auth.UserPrincipal;
import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.Enrollment;
import com.scholr.lms.identity.IdentityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The instructor workspace (Part 12): author courses and lessons, publish, and view cohort rosters.
 * Every action is tenant-scoped by the authenticated instructor's tenant, so an instructor only ever
 * sees and edits their own organization's content. Role-gated to {@code ROLE_INSTRUCTOR} by the
 * security config.
 */
@Controller
public class InstructorController {

    private final CatalogService catalog;
    private final EnrollmentService enrollment;
    private final IdentityService identity;

    public InstructorController(CatalogService catalog, EnrollmentService enrollment, IdentityService identity) {
        this.catalog = catalog;
        this.enrollment = enrollment;
        this.identity = identity;
    }

    private void principal(Model model, UserPrincipal p) {
        model.addAttribute("displayName", p.displayName());
        model.addAttribute("roleLabel", "Instructor");
    }

    @GetMapping("/instructor/courses")
    public String courses(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        List<CourseRow> rows = new ArrayList<>();
        for (Course c : catalog.allCourses()) {
            rows.add(new CourseRow(c.id(), c.title(), c.isPublished(), catalog.lessonCount(c.id())));
        }
        model.addAttribute("courses", rows);
        return "instructor/courses";
    }

    public record CourseRow(UUID id, String title, boolean published, long lessons) {
    }

    @PostMapping("/instructor/courses")
    public String createCourse(@RequestParam String title) {
        catalog.createCourse(title.isBlank() ? "Untitled course" : title.trim());
        return "redirect:/instructor/courses";
    }

    @GetMapping("/instructor/courses/{id}")
    public String course(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID id, Model model) {
        principal(model, p);
        Course course = catalog.findCourse(id).orElseThrow(() -> new IllegalArgumentException("no course " + id));
        model.addAttribute("course", course);
        model.addAttribute("lessons", catalog.lessons(id));
        return "instructor/course";
    }

    @PostMapping("/instructor/courses/{id}/lessons")
    public String addLesson(@PathVariable UUID id, @RequestParam String title, @RequestParam(required = false) String body) {
        catalog.addLesson(id, title.isBlank() ? "Untitled lesson" : title.trim(), body == null ? "" : body);
        return "redirect:/instructor/courses/" + id;
    }

    @PostMapping("/instructor/courses/{id}/publish")
    public String publish(@PathVariable UUID id) {
        catalog.publish(id);
        return "redirect:/instructor/courses/" + id + "?published";
    }

    @GetMapping({"/instructor/cohorts", "/instructor/grading"})
    public String cohorts(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        List<CohortRow> rows = new ArrayList<>();
        for (Course c : catalog.allCourses()) {
            for (Cohort cohort : enrollment.cohortsForCourse(c.id())) {
                List<String> students = new ArrayList<>();
                for (Enrollment e : enrollment.rosterForCohort(cohort.id())) {
                    identity.findUser(e.learnerId()).ifPresent(u -> students.add(u.name()));
                }
                rows.add(new CohortRow(c.title(), cohort.capacity(), cohort.enrolledCount(), students));
            }
        }
        model.addAttribute("cohorts", rows);
        return "instructor/cohorts";
    }

    public record CohortRow(String courseTitle, int capacity, int enrolled, List<String> students) {
    }
}
