package com.scholr.lms.interop;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.interop.domain.XapiStatement;
import org.springframework.stereotype.Component;

/**
 * Translates the platform's own {@link OutboxEvent}s (from Part 5) into xAPI statements. This is the
 * concrete realization of the article's central interop point: <strong>your event stream is your xAPI
 * feed.</strong> You already emit reliable domain events; rather than instrument learning a second time for
 * an external Learning Record Store, you map those events into actor–verb–object statements at the boundary.
 *
 * <p>Pure and deterministic — no Spring state, no clock, no I/O — so the mapping is trivially testable and
 * replay-safe: re-translating the same event yields an equivalent statement, and the LRS dedupes on id.
 * Events the standard has no verb for return empty rather than inventing a statement.
 */
@Component
public class XapiTranslator {

    /** Map one internal event to an xAPI statement, if the event type has a learning verb. */
    public Optional<XapiStatement> toStatement(OutboxEvent event) {
        // payloads in this codebase are a compact "learnerId:objectId" snapshot (see Part 5)
        String[] parts = event.payload().split(":");
        if (parts.length < 2) {
            return Optional.empty();
        }
        UUID actorId = UUID.fromString(parts[0]);
        UUID objectId = UUID.fromString(parts[1]);

        return switch (event.type()) {
            case "enrollment.created" ->
                Optional.of(XapiStatement.of(event.tenantId(), actorId, "registered", objectId, null));
            case "lesson.completed" ->
                Optional.of(XapiStatement.of(event.tenantId(), actorId, "completed", objectId, null));
            default -> Optional.empty(); // no xAPI verb for this event — don't fabricate one
        };
    }
}
