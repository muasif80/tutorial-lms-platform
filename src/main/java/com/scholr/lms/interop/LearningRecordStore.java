package com.scholr.lms.interop;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.interop.domain.XapiStatement;

/**
 * The Learning Record Store seam — where xAPI statements are recorded. An external LRS (Learning Locker,
 * Veracity, a Watershed) plugs in here; {@link InMemoryLrs} is the in-process default for tests and dev.
 * The platform translates its internal events into statements and ships them through this port, exactly the
 * way the broker, event publisher, and payment gateway are isolated behind ports elsewhere in the series.
 */
public interface LearningRecordStore {

    /** Record a statement. Idempotent on the statement id, so re-emitting the same statement is a no-op. */
    void record(XapiStatement statement);

    /** All statements for an actor, scoped to a tenant (the LRS has no Row-Level Security — filter by hand). */
    List<XapiStatement> statementsFor(UUID tenantId, UUID actorId);
}
