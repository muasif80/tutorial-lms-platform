package com.scholr.lms.interop.domain;

import java.util.UUID;

/**
 * An xAPI ("Tin Can") statement in its essential actor–verb–object form, the lingua franca of modern
 * learning-record interoperability. A statement asserts that an actor did something to an object —
 * "learner X completed course Y" — optionally with a result (a score).
 *
 * <p>The key insight the article draws: your internal event stream from Part 5 <em>is</em> your xAPI feed.
 * Rather than instrument the app twice, you translate the events you already emit into xAPI statements at
 * the boundary and ship them to a Learning Record Store. This is a deliberately small, vendor-neutral shape
 * (a real xAPI statement has more optional structure); it carries the tenant id because, like every store
 * outside Postgres, the LRS has no Row-Level Security and must filter by tenant explicitly.
 *
 * @param scorePercent 0–100, or {@code null} when the verb carries no score (e.g. "registered")
 */
public record XapiStatement(
    UUID id,
    UUID tenantId,
    UUID actorId,
    String verb,
    UUID objectId,
    Integer scorePercent
) {

    public static XapiStatement of(UUID tenantId, UUID actorId, String verb, UUID objectId, Integer scorePercent) {
        return new XapiStatement(UUID.randomUUID(), tenantId, actorId, verb, objectId, scorePercent);
    }
}
