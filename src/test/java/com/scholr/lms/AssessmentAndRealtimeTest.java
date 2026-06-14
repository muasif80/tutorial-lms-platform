package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.AssessmentService;
import com.scholr.lms.assessment.domain.Assessment;
import com.scholr.lms.assessment.domain.Attempt;
import com.scholr.lms.assessment.domain.AttemptPolicyException;
import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.assessment.domain.QuestionType;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.realtime.InMemoryMessageBroker;
import com.scholr.lms.realtime.LiveClassRoom;
import com.scholr.lms.realtime.MessageBroker;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 4 guarantees on a real persistence stack (H2): deterministic grading through the
 * public service, idempotent exactly-once submission, server-enforced attempts policy, tenant
 * isolation on attempts, and the real-time fan-out + reconnect-recovery contract. Everything goes
 * through the public services so the module boundaries hold.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssessmentAndRealtimeTest {

    @Autowired
    private IdentityService identity;

    @Autowired
    private AssessmentService assessments;

    @Autowired
    private MessageBroker broker;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID setUpTenant() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        return org.id();
    }

    @Test
    void submission_is_graded_deterministically_and_is_idempotent() {
        setUpTenant();
        AppUser learner = identity.createUser("l1@acme.test", "Learner One");
        UUID courseId = UUID.randomUUID();
        Assessment quiz = assessments.createAssessment(courseId, "Quiz", 3, 0);

        Question q1 = assessments.addQuestion(quiz.id(), QuestionType.SINGLE_CHOICE,
            "2+2?", 5, List.of("3", "4", "5"), Set.of("1"));
        Question q2 = assessments.addQuestion(quiz.id(), QuestionType.SHORT_TEXT,
            "capital of France?", 5, List.of(), Set.of("Paris"));

        Attempt attempt = assessments.startAttempt(quiz.id(), learner.id());
        Map<UUID, Set<String>> answers = Map.of(
            q1.id(), Set.of("1"),       // correct -> 5
            q2.id(), Set.of("paris")    // correct after normalization -> 5
        );

        GradedResult first = assessments.submitAttempt(attempt.id(), answers);
        assertEquals(10, first.score());
        assertEquals(10, first.maxScore());

        // A duplicate submit (double-click / retry) must return the SAME grade, not regrade.
        GradedResult retry = assessments.submitAttempt(attempt.id(),
            Map.of(q1.id(), Set.of("0"), q2.id(), Set.of("London"))); // even with wrong answers
        assertEquals(10, retry.score(), "a duplicate submit returns the first recorded grade, unchanged");
    }

    @Test
    void attempts_policy_is_enforced_server_side() {
        setUpTenant();
        AppUser learner = identity.createUser("l2@acme.test", "Learner Two");
        UUID courseId = UUID.randomUUID();
        Assessment quiz = assessments.createAssessment(courseId, "Single-attempt exam", 1, 0);
        assessments.addQuestion(quiz.id(), QuestionType.SINGLE_CHOICE, "q", 1, List.of("A", "B"), Set.of("0"));

        Attempt a1 = assessments.startAttempt(quiz.id(), learner.id());
        assessments.submitAttempt(a1.id(), Map.of());

        // Only one attempt allowed; a second start is rejected by the server, not the client.
        assertThrows(AttemptPolicyException.class, () -> assessments.startAttempt(quiz.id(), learner.id()));
    }

    @Test
    void start_attempt_is_idempotent_on_the_attempt_number() {
        setUpTenant();
        AppUser learner = identity.createUser("l3@acme.test", "Learner Three");
        UUID courseId = UUID.randomUUID();
        Assessment quiz = assessments.createAssessment(courseId, "Quiz", 0 /* unlimited */, 0);

        // Without an intervening submit, "start the next attempt" resolves to the same attempt #1,
        // not two separate attempts — the natural-key find-or-create caps double-counting.
        Attempt a = assessments.startAttempt(quiz.id(), learner.id());
        Attempt again = assessments.startAttempt(quiz.id(), learner.id());
        assertEquals(a.id(), again.id());
        assertEquals(1, again.attemptNo());
    }

    @Test
    void tenants_cannot_see_each_others_assessments() {
        Organization acme = identity.createOrganization("Acme");
        Organization globex = identity.createOrganization("Globex");
        UUID sharedCourseId = UUID.randomUUID();

        TenantContext.set(TenantId.of(acme.id()));
        Assessment acmeQuiz = assessments.createAssessment(sharedCourseId, "Acme Quiz", 0, 0);

        TenantContext.set(TenantId.of(globex.id()));
        assertTrue(assessments.assessmentsForCourse(sharedCourseId).isEmpty(),
            "Globex must not see Acme's assessment");

        TenantContext.set(TenantId.of(acme.id()));
        List<Assessment> acmeOnly = assessments.assessmentsForCourse(sharedCourseId);
        assertEquals(1, acmeOnly.size());
        assertEquals(acmeQuiz.id(), acmeOnly.get(0).id());
    }

    @Test
    void live_class_fans_out_presence_and_recovers_missed_messages() {
        String roomId = "live:" + UUID.randomUUID();
        LiveClassRoom room = new LiveClassRoom(roomId, broker);

        // Two gateway "nodes" subscribe to the same room via the broker — the fan-out seam.
        StringBuilder nodeA = new StringBuilder();
        StringBuilder nodeB = new StringBuilder();
        try (MessageBroker.Subscription subA = broker.subscribe(roomId, m -> nodeA.append(m).append("\n"));
             MessageBroker.Subscription subB = broker.subscribe(roomId, m -> nodeB.append(m).append("\n"))) {

            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            room.join(alice);
            room.join(bob);
            assertEquals(2, room.presenceCount());

            long s1 = room.publish("hello");
            long s2 = room.publish("world");
            assertEquals(1, s1);
            assertEquals(2, s2);

            // Both nodes received both messages (publish fanned out to every subscriber).
            assertTrue(nodeA.toString().contains("1:hello") && nodeA.toString().contains("2:world"));
            assertTrue(nodeB.toString().contains("1:hello") && nodeB.toString().contains("2:world"));

            // A client that last saw seq 1 reconnects and recovers only what it missed, in order.
            List<LiveClassRoom.SequencedMessage> missed = room.replaySince(1);
            assertEquals(1, missed.size());
            assertEquals(2, missed.get(0).seq());
            assertEquals("world", missed.get(0).payload());

            room.leave(alice);
            assertFalse(room.isPresent(alice));
            assertEquals(1, room.presenceCount());
        }

        // After both subscriptions close, a publish reaches no one (nodes stopped serving the room).
        long beforeSeq = room.currentSequence();
        room.publish("after-close");
        assertNotEquals(beforeSeq, room.currentSequence(), "sequence still advances locally");
    }
}
