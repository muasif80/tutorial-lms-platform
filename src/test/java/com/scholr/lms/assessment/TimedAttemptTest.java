package com.scholr.lms.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.domain.Assessment;
import com.scholr.lms.assessment.domain.Attempt;
import com.scholr.lms.assessment.domain.AttemptStatus;
import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.assessment.domain.QuestionType;
import com.scholr.lms.assessment.internal.AssessmentRepository;
import com.scholr.lms.assessment.internal.AttemptRepository;
import com.scholr.lms.assessment.internal.QuestionRepository;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the time-limit behavior deterministically: a timed attempt submitted past its deadline is
 * graded on whatever was saved and marked {@code EXPIRED} — never rejected (a learner doesn't lose
 * work to a clock) and never silently extended. Lives in the assessment package so it can construct
 * the service with a controllable {@link Clock} via the package-private test seam, exactly as
 * {@code MediaTest} does for {@code SignedUrlIssuer}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TimedAttemptTest {

    @Autowired private IdentityService identity;
    @Autowired private AssessmentRepository assessmentRepo;
    @Autowired private QuestionRepository questionRepo;
    @Autowired private AttemptRepository attemptRepo;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void a_submission_past_the_deadline_is_graded_and_marked_expired() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        AppUser learner = identity.createUser("timed@acme.test", "Timed Learner");

        Instant start = Instant.parse("2026-06-14T10:00:00Z");
        // Service whose clock we control: start at 10:00.
        AssessmentService atStart = new AssessmentService(
            assessmentRepo, questionRepo, attemptRepo, new AutoGrader(), Clock.fixed(start, ZoneOffset.UTC));

        Assessment timed = atStart.createAssessment(UUID.randomUUID(), "Timed exam", 1, 60 /* 60s limit */);
        Question q = atStart.addQuestion(timed.id(), QuestionType.SINGLE_CHOICE, "q", 10,
            List.of("A", "B"), Set.of("0"));
        Attempt attempt = atStart.startAttempt(timed.id(), learner.id());
        assertNotNull(attempt.deadlineAt());

        // Now submit two minutes later — past the 60-second deadline.
        Instant late = start.plus(Duration.ofMinutes(2));
        AssessmentService atLate = new AssessmentService(
            assessmentRepo, questionRepo, attemptRepo, new AutoGrader(), Clock.fixed(late, ZoneOffset.UTC));

        GradedResult result = atLate.submitAttempt(attempt.id(), Map.of(q.id(), Set.of("0")));
        assertEquals(10, result.score(), "work submitted late is still graded, not discarded");

        Attempt reloaded = attemptRepo.findById(attempt.id()).orElseThrow();
        assertEquals(AttemptStatus.EXPIRED, reloaded.status(), "a past-deadline submit is marked EXPIRED");
    }
}
