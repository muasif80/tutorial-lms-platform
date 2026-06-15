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
    private final com.scholr.lms.catalog.AuthoringService authoring;

    public InstructorController(CatalogService catalog, EnrollmentService enrollment, IdentityService identity,
                                com.scholr.lms.catalog.AuthoringService authoring) {
        this.catalog = catalog;
        this.enrollment = enrollment;
        this.identity = identity;
        this.authoring = authoring;
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
        List<LessonRow> lessons = new ArrayList<>();
        for (com.scholr.lms.catalog.domain.Lesson l : catalog.lessons(id)) {
            lessons.add(new LessonRow(l.id(), l.position(), l.title(), authoring.sections(l.id()).size()));
        }
        model.addAttribute("lessons", lessons);
        return "instructor/course";
    }

    public record LessonRow(UUID id, int position, String title, int sections) {
    }

    @PostMapping("/instructor/courses/{id}/lessons")
    public String addLesson(@PathVariable UUID id, @RequestParam String title, @RequestParam(required = false) String body) {
        com.scholr.lms.catalog.domain.Lesson l =
            catalog.addLesson(id, title.isBlank() ? "Untitled lesson" : title.trim(), body == null ? "" : body);
        return "redirect:/instructor/lessons/" + l.id(); // open the new lesson to author its sections
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

    // ---- Part 15: rich lesson authoring (sections + block editor) --------------------------------

    /** Lesson editor: the section table-of-contents + add/reorder/delete + rename/delete the lesson. */
    @GetMapping("/instructor/lessons/{lessonId}")
    public String lesson(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID lessonId, Model model) {
        principal(model, p);
        com.scholr.lms.catalog.domain.Lesson l = authoring.lesson(lessonId);
        model.addAttribute("lesson", l);
        model.addAttribute("course", catalog.findCourse(l.courseId()).orElse(null));
        model.addAttribute("sections", authoring.sections(lessonId));
        return "instructor/lesson";
    }

    @PostMapping("/instructor/lessons/{lessonId}/title")
    public String renameLesson(@PathVariable UUID lessonId, @RequestParam String title) {
        authoring.renameLesson(lessonId, title);
        return "redirect:/instructor/lessons/" + lessonId;
    }

    @PostMapping("/instructor/lessons/{lessonId}/move")
    public String moveLesson(@PathVariable UUID lessonId, @RequestParam String dir) {
        UUID courseId = authoring.lesson(lessonId).courseId();
        authoring.moveLesson(lessonId, "up".equals(dir));
        return "redirect:/instructor/courses/" + courseId;
    }

    @PostMapping("/instructor/lessons/{lessonId}/delete")
    public String deleteLesson(@PathVariable UUID lessonId) {
        UUID courseId = authoring.lesson(lessonId).courseId();
        authoring.deleteLesson(lessonId);
        return "redirect:/instructor/courses/" + courseId;
    }

    @PostMapping("/instructor/lessons/{lessonId}/sections")
    public String addSection(@PathVariable UUID lessonId, @RequestParam(required = false) String title) {
        com.scholr.lms.catalog.domain.Section s =
            authoring.addSection(lessonId, title == null || title.isBlank() ? "New section" : title.trim());
        return "redirect:/instructor/sections/" + s.id() + "/edit"; // jump straight into the block editor
    }

    /** Section editor: the block editor (Editor.js) bound to one section. */
    @GetMapping("/instructor/sections/{sectionId}/edit")
    public String editSection(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID sectionId, Model model) {
        principal(model, p);
        com.scholr.lms.catalog.domain.Section s = authoring.section(sectionId);
        model.addAttribute("section", s);
        model.addAttribute("lesson", authoring.lesson(s.lessonId()));
        return "instructor/section";
    }

    @PostMapping("/instructor/sections/{sectionId}")
    public String saveSection(@PathVariable UUID sectionId, @RequestParam String title,
                              @RequestParam(name = "contentJson", required = false) String contentJson) {
        com.scholr.lms.catalog.domain.Section saved = authoring.saveSection(sectionId, title, contentJson);
        return "redirect:/instructor/lessons/" + saved.lessonId();
    }

    @PostMapping("/instructor/sections/{sectionId}/move")
    public String moveSection(@PathVariable UUID sectionId, @RequestParam String dir) {
        UUID lessonId = authoring.section(sectionId).lessonId();
        authoring.moveSection(sectionId, "up".equals(dir));
        return "redirect:/instructor/lessons/" + lessonId;
    }

    @PostMapping("/instructor/sections/{sectionId}/delete")
    public String deleteSection(@PathVariable UUID sectionId) {
        UUID lessonId = authoring.section(sectionId).lessonId();
        authoring.deleteSection(sectionId);
        return "redirect:/instructor/lessons/" + lessonId;
    }

    /** Image upload endpoint for the block editor (Editor.js image tool). Returns its expected JSON. */
    @PostMapping("/instructor/upload/image")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> uploadImage(
            @RequestParam("image") org.springframework.web.multipart.MultipartFile image) throws java.io.IOException {
        if (image.isEmpty() || image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            return java.util.Map.of("success", 0);
        }
        UUID id = authoring.storeBlob(image.getContentType(), image.getOriginalFilename(), image.getBytes());
        return java.util.Map.of("success", 1, "file", java.util.Map.of("url", "/media/blob/" + id));
    }
}
