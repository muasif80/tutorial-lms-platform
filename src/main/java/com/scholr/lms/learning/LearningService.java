package com.scholr.lms.learning;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.scholr.lms.learning.domain.LessonCompletion;
import com.scholr.lms.learning.internal.LessonCompletionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the Learning &amp; Progress context (Part 13). Progress is modeled as a fold over
 * idempotent completion facts: a learner's progress in a course is simply how many of its lessons they've
 * marked complete. Marking a lesson complete is idempotent — a repeated click resolves to the existing
 * fact instead of inflating the count — which is the same idempotent-write discipline used for enrolment,
 * submission, and billing earlier in the series, now applied to progress.
 */
@Service
public class LearningService {

    private final LessonCompletionRepository completions;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LearningService(LessonCompletionRepository completions) {
        this(completions, Clock.systemUTC());
    }

    /** Seam for tests: inject a fixed {@link Clock} for deterministic completion timestamps. */
    LearningService(LessonCompletionRepository completions, Clock clock) {
        this.completions = completions;
        this.clock = clock;
    }

    /** Mark a lesson complete for a learner — idempotent on {@code (learner, lesson)}. */
    @Transactional
    public void markComplete(UUID learnerId, UUID courseId, UUID lessonId) {
        if (completions.existsByLearnerIdAndLessonId(learnerId, lessonId)) {
            return; // already done — no double counting
        }
        completions.save(LessonCompletion.of(learnerId, courseId, lessonId, Instant.now(clock)));
    }

    /** The lesson ids a learner has completed in a course (used to tick off the player). */
    @Transactional(readOnly = true)
    public Set<UUID> completedLessonIds(UUID learnerId, UUID courseId) {
        return completions.findByLearnerIdAndCourseId(learnerId, courseId).stream()
            .map(LessonCompletion::lessonId)
            .collect(Collectors.toSet());
    }

    /** How many lessons a learner has completed in a course. */
    @Transactional(readOnly = true)
    public long completedCount(UUID learnerId, UUID courseId) {
        return completions.countByLearnerIdAndCourseId(learnerId, courseId);
    }

    /** A course is complete when every lesson is done (and there is at least one lesson). */
    @Transactional(readOnly = true)
    public boolean isCourseComplete(UUID learnerId, UUID courseId, long totalLessons) {
        return totalLessons > 0 && completedCount(learnerId, courseId) >= totalLessons;
    }

    /** Part 14: total lesson completions across the tenant — an engagement metric for the admin reports. */
    @Transactional(readOnly = true)
    public long totalCompletions() {
        return completions.count();
    }
}
