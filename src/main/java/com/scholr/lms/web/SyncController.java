package com.scholr.lms.web;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.sync.SyncService;
import com.scholr.lms.sync.domain.CourseSyncState;
import com.scholr.lms.sync.domain.SyncBatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for offline sync. A client that has been offline POSTs its accumulated changes here on
 * reconnect; the endpoint is idempotent, so a client may safely retry a sync whose response it never saw.
 */
@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncService sync;

    public SyncController(SyncService sync) {
        this.sync = sync;
    }

    public record SyncRequest(
        UUID learnerId,
        UUID courseId,
        Set<UUID> completedLessons,
        UUID lastPositionLesson,
        Instant lastPositionAt,
        long baseVersion
    ) {
    }

    public record SyncStateView(
        List<UUID> completedLessons,
        int completedCount,
        UUID lastPositionLesson,
        long version
    ) {
    }

    private static SyncStateView toView(CourseSyncState s) {
        return new SyncStateView(List.copyOf(s.completedLessons()), s.completedCount(),
            s.lastPositionLesson(), s.version());
    }

    /** Merge an offline batch and return the converged state. */
    @PostMapping("/sync")
    public SyncStateView sync(@RequestBody SyncRequest req) {
        SyncBatch batch = new SyncBatch(req.completedLessons(), req.lastPositionLesson(),
            req.lastPositionAt(), req.baseVersion());
        return toView(sync.sync(req.learnerId(), req.courseId(), batch));
    }

    /** Read the current converged state (e.g. to hydrate a freshly-opened client). */
    @GetMapping("/sync")
    public SyncStateView current(@RequestParam UUID learnerId, @RequestParam UUID courseId) {
        return toView(sync.current(learnerId, courseId));
    }
}
