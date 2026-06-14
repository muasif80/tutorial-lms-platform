package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.events.InMemoryEventPublisher;
import com.scholr.lms.events.OutboxRelay;
import com.scholr.lms.events.ProgressProjection;
import com.scholr.lms.events.domain.LearnerProgress;
import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.events.internal.LearnerProgressRepository;
import com.scholr.lms.events.internal.OutboxEventRepository;
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
 * Proves the Part 5 guarantees on a real persistence stack (H2): the transactional outbox is written
 * atomically with the business change, the relay ships events at-least-once, the consumer is idempotent
 * (a duplicate delivery never double-counts), progress is computed correctly from the stream, and the
 * whole pipeline is tenant-isolated. Everything goes through the public services so module boundaries hold.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EventsPipelineTest {

    @Autowired private IdentityService identity;
    @Autowired private CatalogService catalog;
    @Autowired private EnrollmentService enrollment;
    @Autowired private OutboxRelay relay;
    @Autowired private InMemoryEventPublisher publisher;
    @Autowired private ProgressProjection projection;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private LearnerProgressRepository progress;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void enroll_writes_an_outbox_event_in_the_same_transaction() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        AppUser learner = identity.createUser("e1@acme.test", "Learner");
        Course course = catalog.createCourse("Course");
        Cohort cohort = enrollment.createCohort(course.id(), 10);

        enrollment.enroll(cohort.id(), learner.id());

        // The event was persisted atomically with the enrollment — no separate publish step.
        List<OutboxEvent> events = outbox.findByPublishedFalseOrderByOccurredAtAsc();
        assertEquals(1, events.size(), "enroll writes exactly one outbox event");
        assertEquals("enrollment.created", events.get(0).type());
        assertEquals(learner.id() + ":" + course.id(), events.get(0).payload());

        // The idempotent retry must NOT emit a second event.
        enrollment.enroll(cohort.id(), learner.id());
        assertEquals(1, outbox.findByPublishedFalseOrderByOccurredAtAsc().size(),
            "a retried enroll does not duplicate the event");
    }

    @Test
    void relay_ships_unpublished_events_then_marks_them_published() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        AppUser learner = identity.createUser("e2@acme.test", "Learner");
        Course course = catalog.createCourse("Course");
        Cohort cohort = enrollment.createCohort(course.id(), 10);
        enrollment.enroll(cohort.id(), learner.id());

        int shipped = relay.relayPending();
        assertEquals(1, shipped, "the relay ships the pending event");
        assertTrue(outbox.findByPublishedFalseOrderByOccurredAtAsc().isEmpty(),
            "shipped events are marked published");

        // Re-running the relay ships nothing (the event is already published) — no duplicate publish.
        assertEquals(0, relay.relayPending());
    }

    @Test
    void consumer_is_idempotent_and_computes_progress_from_the_stream() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        AppUser learner = identity.createUser("e3@acme.test", "Learner");
        Course course = catalog.createCourse("Course");
        UUID learnerId = learner.id();
        UUID courseId = course.id();

        // Build a synthetic stream: one enrollment + two lesson completions for the same learner.
        OutboxEvent enrolled = OutboxEvent.of("enrollment.created", UUID.randomUUID(),
            learnerId + ":" + courseId, Instant.now());
        OutboxEvent lesson1 = OutboxEvent.of("lesson.completed", UUID.randomUUID(),
            learnerId + ":" + courseId, Instant.now());
        OutboxEvent lesson2 = OutboxEvent.of("lesson.completed", UUID.randomUUID(),
            learnerId + ":" + courseId, Instant.now());

        assertTrue(projection.apply(enrolled));
        assertTrue(projection.apply(lesson1));
        // Deliver lesson1 AGAIN (at-least-once delivery) — must be skipped, not double-counted.
        assertFalse(projection.apply(lesson1), "a duplicate delivery is skipped");
        assertTrue(projection.apply(lesson2));

        LearnerProgress p = progress.findByLearnerIdAndCourseId(learnerId, courseId).orElseThrow();
        assertTrue(p.isEnrolled());
        assertEquals(2, p.lessonsCompleted(),
            "exactly two lessons counted despite the duplicate delivery");
    }

    @Test
    void tenants_cannot_see_each_others_events() {
        Organization acme = identity.createOrganization("Acme");
        Organization globex = identity.createOrganization("Globex");

        TenantContext.set(TenantId.of(acme.id()));
        AppUser acmeLearner = identity.createUser("a@acme.test", "A");
        Course acmeCourse = catalog.createCourse("Acme Course");
        Cohort acmeCohort = enrollment.createCohort(acmeCourse.id(), 5);
        enrollment.enroll(acmeCohort.id(), acmeLearner.id());

        // Globex sees none of Acme's outbox events — the stream is tenant-isolated like everything else.
        TenantContext.set(TenantId.of(globex.id()));
        assertTrue(outbox.findByPublishedFalseOrderByOccurredAtAsc().isEmpty(),
            "Globex must not see Acme's events");

        TenantContext.set(TenantId.of(acme.id()));
        assertEquals(1, outbox.findByPublishedFalseOrderByOccurredAtAsc().size(),
            "Acme sees only its own event");
    }
}
