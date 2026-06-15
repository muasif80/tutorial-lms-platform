package com.scholr.lms.web.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.AssessmentService;
import com.scholr.lms.assessment.domain.Assessment;
import com.scholr.lms.assessment.domain.Attempt;
import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.auth.UserPrincipal;
import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.catalog.domain.Lesson;
import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Enrollment;
import com.scholr.lms.learning.LearningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The student learning flow (Part 13): browse the published catalogue, enrol, work through a course's
 * lessons in a player, take an auto-graded assessment, and watch progress build toward a completion
 * certificate. Role-gated to {@code ROLE_STUDENT}; every action is scoped to the authenticated learner
 * (their id is {@code principal.userId()}) and their tenant (pinned by the {@code TenantPrincipalFilter}).
 */
@Controller
public class StudentController {

    private static final double PASS_THRESHOLD = 70.0;

    private final CatalogService catalog;
    private final EnrollmentService enrollment;
    private final LearningService learning;
    private final AssessmentService assessment;

    public StudentController(CatalogService catalog, EnrollmentService enrollment,
                            LearningService learning, AssessmentService assessment) {
        this.catalog = catalog;
        this.enrollment = enrollment;
        this.learning = learning;
        this.assessment = assessment;
    }

    private void principal(Model model, UserPrincipal p) {
        model.addAttribute("displayName", p.displayName());
        model.addAttribute("roleLabel", "Student");
    }

    /** The set of course ids this learner is already enrolled in. */
    private Set<UUID> enrolledCourseIds(UUID learnerId) {
        Set<UUID> ids = new HashSet<>();
        for (Enrollment e : enrollment.enrollmentsForLearner(learnerId)) {
            ids.add(enrollment.cohort(e.cohortId()).courseId());
        }
        return ids;
    }

    // ---- dashboard -------------------------------------------------------------------------------

    @GetMapping("/learn")
    public String dashboard(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        List<MyCourseRow> mine = myCourseRows(p.userId());
        // "continue learning" = the first enrolled course that isn't finished yet
        MyCourseRow resume = mine.stream().filter(c -> !c.complete()).findFirst().orElse(null);
        model.addAttribute("courses", mine);
        model.addAttribute("resume", resume);
        model.addAttribute("enrolledCount", mine.size());
        model.addAttribute("completedCount", mine.stream().filter(MyCourseRow::complete).count());
        return "student/dashboard";
    }

    // ---- catalogue -------------------------------------------------------------------------------

    @GetMapping("/learn/catalog")
    public String catalog(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        Set<UUID> enrolled = enrolledCourseIds(p.userId());
        List<CatalogRow> rows = new ArrayList<>();
        for (Course c : catalog.publishedCourses()) {
            rows.add(new CatalogRow(c.id(), c.title(), catalog.lessonCount(c.id()), enrolled.contains(c.id())));
        }
        model.addAttribute("courses", rows);
        return "student/catalog";
    }

    public record CatalogRow(UUID id, String title, long lessons, boolean enrolled) {
    }

    @PostMapping("/learn/enroll")
    public String enroll(@AuthenticationPrincipal UserPrincipal p, @RequestParam UUID courseId) {
        enrollment.enrollInCourse(courseId, p.userId());
        return "redirect:/learn/courses/" + courseId;
    }

    // ---- my learning -----------------------------------------------------------------------------

    @GetMapping("/learn/courses")
    public String myCourses(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        model.addAttribute("courses", myCourseRows(p.userId()));
        return "student/courses";
    }

    /** Build a learner's "my courses" rows (deduplicated by course), with progress on each. */
    private List<MyCourseRow> myCourseRows(UUID learnerId) {
        Map<UUID, MyCourseRow> byCourse = new LinkedHashMap<>();
        for (Enrollment e : enrollment.enrollmentsForLearner(learnerId)) {
            UUID courseId = enrollment.cohort(e.cohortId()).courseId();
            if (byCourse.containsKey(courseId)) {
                continue;
            }
            catalog.findCourse(courseId).ifPresent(course -> {
                long total = catalog.lessonCount(courseId);
                long done = learning.completedCount(learnerId, courseId);
                int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
                byCourse.put(courseId, new MyCourseRow(courseId, course.title(), done, total, percent,
                    learning.isCourseComplete(learnerId, courseId, total)));
            });
        }
        return new ArrayList<>(byCourse.values());
    }

    public record MyCourseRow(UUID id, String title, long done, long total, int percent, boolean complete) {
    }

    // ---- the course player -----------------------------------------------------------------------

    @GetMapping("/learn/courses/{courseId}")
    public String player(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID courseId, Model model) {
        principal(model, p);
        UUID learnerId = p.userId();
        Course course = catalog.findCourse(courseId)
            .orElseThrow(() -> new IllegalArgumentException("no course " + courseId));

        Set<UUID> completed = learning.completedLessonIds(learnerId, courseId);
        List<LessonView> lessons = new ArrayList<>();
        for (Lesson l : catalog.lessons(courseId)) {
            lessons.add(new LessonView(l.id(), l.position(), l.title(), l.body(), completed.contains(l.id())));
        }

        List<AssessmentView> quizzes = new ArrayList<>();
        for (Assessment a : assessment.assessmentsForCourse(courseId)) {
            GradedResult best = assessment.bestResult(a.id(), learnerId).orElse(null);
            quizzes.add(new AssessmentView(a.id(), a.title(),
                best == null ? null : best.score(), best == null ? null : best.maxScore()));
        }

        long total = lessons.size();
        long done = completed.size();
        model.addAttribute("course", course);
        model.addAttribute("lessons", lessons);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("done", done);
        model.addAttribute("total", total);
        model.addAttribute("percent", total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        model.addAttribute("complete", total > 0 && done >= total);
        return "student/player";
    }

    public record LessonView(UUID id, int position, String title, String body, boolean completed) {
    }

    public record AssessmentView(UUID id, String title, Integer bestScore, Integer maxScore) {
    }

    @PostMapping("/learn/courses/{courseId}/lessons/{lessonId}/complete")
    public String complete(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID courseId,
                           @PathVariable UUID lessonId) {
        learning.markComplete(p.userId(), courseId, lessonId);
        return "redirect:/learn/courses/" + courseId + "#lesson-" + lessonId;
    }

    // ---- the assessment --------------------------------------------------------------------------

    @GetMapping("/learn/courses/{courseId}/assessments/{assessmentId}")
    public String quiz(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID courseId,
                       @PathVariable UUID assessmentId, Model model) {
        principal(model, p);
        Attempt attempt = assessment.startAttempt(assessmentId, p.userId()); // resume or start
        List<QuestionView> questions = new ArrayList<>();
        for (Question q : assessment.questions(assessmentId)) {
            questions.add(new QuestionView(q.id(), q.type().name(), q.prompt(), q.points(), q.options()));
        }
        model.addAttribute("courseId", courseId);
        model.addAttribute("assessmentId", assessmentId);
        model.addAttribute("attemptId", attempt.id());
        model.addAttribute("questions", questions);
        return "student/quiz";
    }

    public record QuestionView(UUID id, String type, String prompt, int points, List<String> options) {
    }

    @PostMapping("/learn/courses/{courseId}/assessments/{assessmentId}/submit")
    public String submit(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID courseId,
                         @PathVariable UUID assessmentId, @RequestParam UUID attemptId,
                         jakarta.servlet.http.HttpServletRequest request, Model model) {
        // Collect answers per question id from the form (radio/checkbox indices, or short text).
        Map<UUID, Set<String>> answers = new java.util.HashMap<>();
        for (Question q : assessment.questions(assessmentId)) {
            String[] vals = request.getParameterValues("q_" + q.id());
            if (vals != null) {
                Set<String> set = new HashSet<>();
                for (String v : vals) {
                    if (v != null && !v.isBlank()) {
                        set.add(v.trim());
                    }
                }
                answers.put(q.id(), set);
            }
        }
        GradedResult result = assessment.submitAttempt(attemptId, answers); // idempotent

        principal(model, p);
        model.addAttribute("courseId", courseId);
        model.addAttribute("score", result.score());
        model.addAttribute("maxScore", result.maxScore());
        model.addAttribute("percent", (int) Math.round(result.percentage()));
        model.addAttribute("pass", result.isPass(PASS_THRESHOLD));
        return "student/result";
    }

    // ---- progress & certificates -----------------------------------------------------------------

    @GetMapping("/learn/progress")
    public String progress(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        model.addAttribute("courses", myCourseRows(p.userId()));
        return "student/progress";
    }
}
