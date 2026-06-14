package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import com.scholr.lms.sync.SyncService;
import com.scholr.lms.sync.domain.CourseSyncState;
import com.scholr.lms.sync.domain.SyncBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 9 offline-sync guarantees on a real persistence stack (H2): completed lessons merge by
 * conflict-free union across devices, the position cursor resolves by last-write-wins, re-syncing the same
 * batch is idempotent, and the whole thing is tenant-isolated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OfflineSyncTest {

    @Autowired private IdentityService identity;
    @Autowired private SyncService sync;

    private final UUID course = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID learnerInTenant(String org, String email) {
        Organization o = identity.createOrganization(org);
        TenantContext.set(TenantId.of(o.id()));
        return identity.createUser(email, "Learner").id();
    }

    @Test
    void two_devices_offline_merge_completed_lessons_conflict_free() {
        UUID learner = learnerInTenant("Acme", "sync1@acme.test");
        UUID l1 = UUID.randomUUID(), l2 = UUID.randomUUID(), l3 = UUID.randomUUID();

        // Device A (on a plane) completed lessons 1 and 2.
        sync.sync(learner, course, new SyncBatch(Set.of(l1, l2), null, null, 0));
        // Device B (offline elsewhere) completed lessons 2 and 3 — note the overlap on lesson 2.
        CourseSyncState merged = sync.sync(learner, course, new SyncBatch(Set.of(l2, l3), null, null, 0));

        // Union of both, no loss, no duplicate — neither device's work is overwritten.
        assertEquals(3, merged.completedCount());
        assertTrue(merged.completedLessons().containsAll(Set.of(l1, l2, l3)));
    }

    @Test
    void re_syncing_the_same_batch_is_idempotent() {
        UUID learner = learnerInTenant("Acme", "sync2@acme.test");
        UUID l1 = UUID.randomUUID(), l2 = UUID.randomUUID();
        SyncBatch batch = new SyncBatch(Set.of(l1, l2), null, null, 0);

        sync.sync(learner, course, batch);
        CourseSyncState again = sync.sync(learner, course, batch); // a retry after a dropped response
        assertEquals(2, again.completedCount(), "re-syncing the same batch changes nothing");
    }

    @Test
    void position_cursor_resolves_by_last_write_wins() {
        UUID learner = learnerInTenant("Acme", "sync3@acme.test");
        UUID early = UUID.randomUUID(), late = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-06-14T10:00:00Z");
        Instant t1 = Instant.parse("2026-06-14T10:05:00Z");

        // The LATER edit arrives FIRST...
        sync.sync(learner, course, new SyncBatch(Set.of(), late, t1, 0));
        // ...then the EARLIER edit arrives (out of order). It must NOT clobber the newer cursor.
        CourseSyncState merged = sync.sync(learner, course, new SyncBatch(Set.of(), early, t0, 0));

        assertEquals(late, merged.lastPositionLesson(), "the newest position wins regardless of arrival order");
    }

    @Test
    void offline_progress_is_tenant_isolated() {
        UUID acmeLearner = learnerInTenant("Acme", "sync-a@acme.test");
        sync.sync(acmeLearner, course, new SyncBatch(Set.of(UUID.randomUUID()), null, null, 0));
        assertEquals(1, sync.current(acmeLearner, course).completedCount());

        // A different tenant sees no state for the same learner/course id space.
        learnerInTenant("Globex", "sync-g@globex.test"); // switches TenantContext to Globex
        assertFalse(sync.current(acmeLearner, course).completedCount() > 0,
            "Globex's tenant context cannot see Acme's offline progress");
    }
}
