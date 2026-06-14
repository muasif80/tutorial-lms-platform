package com.scholr.lms.events;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.scholr.lms.events.domain.LearnerProgress;
import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.events.domain.ProcessedEvent;
import com.scholr.lms.events.internal.LearnerProgressRepository;
import com.scholr.lms.events.internal.ProcessedEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * An <strong>idempotent consumer</strong> that builds the {@link LearnerProgress} read model from the
 * event stream. It is the concrete example of the article's central delivery decision: the broker
 * delivers <em>at least once</em>, and this consumer turns that into exactly-once <em>effects</em> by
 * recording each event id in {@link ProcessedEvent} before acting on it, inside one transaction.
 *
 * <p>The flow for every event: if its id is already in {@code processed_events}, skip it (a duplicate
 * delivery); otherwise apply it to the projection and record the id — both committing together, so a
 * crash mid-apply re-delivers the event and re-applies cleanly. Replaying the entire stream therefore
 * reproduces identical numbers, and the projection can be rebuilt from history at any time.
 *
 * <p>Progress is a projection, never the source of truth: it is eventually consistent with the
 * enrollment and learning aggregates, and that is exactly what an analytics read model should be.
 */
@Component
public class ProgressProjection {

    private final ProcessedEventRepository processed;
    private final LearnerProgressRepository progress;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ProgressProjection(ProcessedEventRepository processed, LearnerProgressRepository progress) {
        this(processed, progress, Clock.systemUTC());
    }

    ProgressProjection(ProcessedEventRepository processed, LearnerProgressRepository progress, Clock clock) {
        this.processed = processed;
        this.progress = progress;
        this.clock = clock;
    }

    /**
     * Apply one event to the progress projection, exactly once in effect.
     *
     * @return {@code true} if the event was applied; {@code false} if it was a duplicate and skipped
     */
    @Transactional
    public boolean apply(OutboxEvent event) {
        if (processed.existsById(event.id())) {
            return false; // already handled — at-least-once delivery, exactly-once effect
        }

        switch (event.type()) {
            case "enrollment.created" -> {
                LearnerProgress row = upsert(event);
                row.markEnrolled();
                progress.save(row);
            }
            case "lesson.completed" -> {
                LearnerProgress row = upsert(event);
                row.incrementLessonsCompleted();
                progress.save(row);
            }
            default -> { /* an event this projection doesn't care about; still mark it processed */ }
        }

        processed.save(new ProcessedEvent(event.id(), Instant.now(clock)));
        return true;
    }

    /**
     * The projection row for the (learner, course) in the event payload, created if absent. The payload
     * is "learnerId:courseId" — a deliberately tiny, self-contained snapshot so the consumer never has
     * to call back into the producing aggregate (the references-by-id rule applied across the stream).
     */
    private LearnerProgress upsert(OutboxEvent event) {
        String[] parts = event.payload().split(":");
        UUID learnerId = UUID.fromString(parts[0]);
        UUID courseId = UUID.fromString(parts[1]);
        LearnerProgress row = progress.findByLearnerIdAndCourseId(learnerId, courseId)
            .orElseGet(() -> progress.save(LearnerProgress.start(learnerId, courseId)));
        return row;
    }
}
