package com.scholr.lms.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.AssessmentService;
import com.scholr.lms.assessment.domain.Assessment;
import com.scholr.lms.assessment.domain.Attempt;
import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.assessment.domain.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the Assessment context. The grading logic and the attempts policy live in the
 * service and the domain, not here; the controller only maps JSON. Note the submit endpoint
 * returns the same grade on a repeated call — idempotency is a property of the system, surfaced
 * honestly to the client, so a retry after a dropped response is always safe.
 */
@RestController
@RequestMapping("/api")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    public record CreateAssessment(UUID courseId, String title, int maxAttempts, int timeLimitSeconds) {
    }

    public record AssessmentView(UUID id, UUID courseId, String title, int maxAttempts, int timeLimitSeconds) {
    }

    private static AssessmentView toView(Assessment a) {
        return new AssessmentView(a.id(), a.courseId(), a.title(), a.maxAttempts(), a.timeLimitSeconds());
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentView create(@RequestBody CreateAssessment request) {
        return toView(assessmentService.createAssessment(
            request.courseId(), request.title(), request.maxAttempts(), request.timeLimitSeconds()));
    }

    public record AddQuestion(QuestionType type, String prompt, int points,
                              List<String> options, Set<String> answerKey) {
    }

    public record QuestionView(UUID id, String type, String prompt, int points, List<String> options) {
    }

    /** Note: the answer key is deliberately NOT in the response — it never leaves the server. */
    @PostMapping("/assessments/{assessmentId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionView addQuestion(@PathVariable UUID assessmentId, @RequestBody AddQuestion request) {
        Question q = assessmentService.addQuestion(assessmentId, request.type(), request.prompt(),
            request.points(), request.options(), request.answerKey());
        return new QuestionView(q.id(), q.type().name(), q.prompt(), q.points(), q.options());
    }

    public record AttemptView(UUID id, int attemptNo, String status, String deadlineAt) {
    }

    @PostMapping("/assessments/{assessmentId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptView startAttempt(@PathVariable UUID assessmentId, @RequestBody UUID learnerId) {
        Attempt a = assessmentService.startAttempt(assessmentId, learnerId);
        return new AttemptView(a.id(), a.attemptNo(), a.status().name(),
            a.deadlineAt() == null ? null : a.deadlineAt().toString());
    }

    public record Submit(Map<UUID, Set<String>> answers) {
    }

    public record GradeView(int score, int maxScore, double percentage) {
    }

    /** Idempotent: re-submitting the same attempt returns the same recorded grade. */
    @PostMapping("/attempts/{attemptId}/submit")
    public GradeView submit(@PathVariable UUID attemptId, @RequestBody Submit request) {
        GradedResult r = assessmentService.submitAttempt(attemptId, request.answers());
        return new GradeView(r.score(), r.maxScore(), r.percentage());
    }

    @GetMapping("/courses/{courseId}/assessments")
    public List<AssessmentView> forCourse(@PathVariable UUID courseId) {
        return assessmentService.assessmentsForCourse(courseId).stream().map(AssessmentController::toView).toList();
    }
}
