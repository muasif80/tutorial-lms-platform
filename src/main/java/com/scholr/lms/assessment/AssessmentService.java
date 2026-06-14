package com.scholr.lms.assessment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.domain.Assessment;
import com.scholr.lms.assessment.domain.Attempt;
import com.scholr.lms.assessment.domain.AttemptPolicyException;
import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.assessment.domain.QuestionType;
import com.scholr.lms.assessment.internal.AssessmentRepository;
import com.scholr.lms.assessment.internal.AttemptRepository;
import com.scholr.lms.assessment.internal.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the Assessment context: author an assessment and its question bank, start a
 * learner attempt (subject to the attempts policy), and submit an attempt for deterministic
 * grading. Every query is tenant-scoped by Hibernate.
 *
 * <p>The two hard problems this class solves are the ones the article is about:
 * <ol>
 *   <li><b>Attempts policy</b> — the server, not the client, decides whether another attempt is
 *       allowed and when an attempt has expired. A timed attempt past its deadline is graded on
 *       whatever was saved and marked {@code EXPIRED}, never silently extended.</li>
 *   <li><b>Exactly-once submission</b> — grading is computed by the pure {@link AutoGrader} and
 *       recorded through {@link Attempt#submit}, which is one-way and idempotent. A double-click,
 *       a client retry, or a redelivered queue message all resolve to the <em>same</em> stored
 *       grade. This is the Part 2 idempotent-write discipline applied to exam submission.</li>
 * </ol>
 */
@Service
public class AssessmentService {

    private final AssessmentRepository assessments;
    private final QuestionRepository questions;
    private final AttemptRepository attempts;
    private final AutoGrader grader;
    private final Clock clock;

    public AssessmentService(AssessmentRepository assessments, QuestionRepository questions,
                             AttemptRepository attempts) {
        this(assessments, questions, attempts, new AutoGrader(), Clock.systemUTC());
    }

    /** Seam for tests: inject a fixed {@link Clock} to drive time-limit behavior deterministically. */
    AssessmentService(AssessmentRepository assessments, QuestionRepository questions,
                      AttemptRepository attempts, AutoGrader grader, Clock clock) {
        this.assessments = assessments;
        this.questions = questions;
        this.attempts = attempts;
        this.grader = grader;
        this.clock = clock;
    }

    @Transactional
    public Assessment createAssessment(UUID courseId, String title, int maxAttempts, int timeLimitSeconds) {
        return assessments.save(Assessment.of(courseId, title, maxAttempts, timeLimitSeconds));
    }

    @Transactional
    public Question addQuestion(UUID assessmentId, QuestionType type, String prompt, int points,
                                List<String> options, Set<String> answerKey) {
        loadAssessment(assessmentId); // ensures the assessment exists in this tenant
        return questions.save(Question.of(assessmentId, type, prompt, points, options, answerKey));
    }

    /** The current tenant's assessments for a course. */
    @Transactional(readOnly = true)
    public List<Assessment> assessmentsForCourse(UUID courseId) {
        return assessments.findByCourseId(courseId);
    }

    /**
     * Start a learner's next attempt — policy-checked and idempotent on the attempt number.
     *
     * <p>The attempt count comes from the database, not the request, so the limit cannot be
     * bypassed by a client. The new attempt's number is {@code existing + 1}; if a concurrent
     * start already created that number, the unique {@code (assessment, learner, attempt_no)}
     * constraint plus this find-or-create resolves both callers to the same attempt instead of
     * double-counting.
     */
    @Transactional
    public Attempt startAttempt(UUID assessmentId, UUID learnerId) {
        Assessment assessment = loadAssessment(assessmentId);
        List<Attempt> existing = attempts.findByAssessmentIdAndLearnerId(assessmentId, learnerId);
        long started = existing.size();
        if (!assessment.allowsAnotherAttempt(started)) {
            throw new AttemptPolicyException(assessmentId,
                "no attempts remaining (" + started + "/" + assessment.maxAttempts() + ")");
        }
        int nextNo = (int) started + 1;
        return attempts.findByAssessmentIdAndLearnerIdAndAttemptNo(assessmentId, learnerId, nextNo)
            .orElseGet(() -> {
                Instant now = clock.instant();
                Instant deadline = assessment.isTimed()
                    ? now.plusSeconds(assessment.timeLimitSeconds())
                    : null;
                return attempts.save(Attempt.start(assessmentId, learnerId, nextNo, now, deadline));
            });
    }

    /**
     * Submit an attempt for grading — idempotent and exactly-once.
     *
     * <p>The score is computed by the pure {@link AutoGrader} over the assessment's question bank
     * and the learner's answers. {@link Attempt#submit} records it only if the attempt has not
     * already been graded; a duplicate submission therefore returns the <em>already-recorded</em>
     * result without regrading or overwriting it. If the attempt's deadline has passed, it is
     * graded on the submitted answers and marked {@code EXPIRED} rather than rejected — a learner
     * never loses work to a clock they couldn't control, but they also can't extend it.
     *
     * @param answers learner answers keyed by question id
     * @return the recorded grade (the first one, on a duplicate submit)
     */
    @Transactional
    public GradedResult submitAttempt(UUID attemptId, Map<UUID, Set<String>> answers) {
        Attempt attempt = attempts.findById(attemptId)
            .orElseThrow(() -> new IllegalArgumentException("attempt not found: " + attemptId));

        if (attempt.isGraded()) {
            return new GradedResult(attempt.score(), attempt.maxScore());
        }

        List<Question> bank = questions.findByAssessmentId(attempt.assessmentId());
        GradedResult result = grader.grade(bank, answers);

        Instant now = clock.instant();
        boolean expired = attempt.isExpiredAt(now);
        boolean graded = attempt.submit(result.score(), result.maxScore(), now, expired);
        attempts.save(attempt);

        // If a concurrent submit beat us (graded == false), return the persisted result.
        return graded ? result : new GradedResult(attempt.score(), attempt.maxScore());
    }

    private Assessment loadAssessment(UUID assessmentId) {
        return assessments.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("assessment not found: " + assessmentId));
    }
}
