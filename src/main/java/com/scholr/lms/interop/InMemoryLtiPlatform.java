package com.scholr.lms.interop;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * In-process {@link LtiPlatform} — records grade passbacks so the flow is testable without a real AGS
 * endpoint. Production swaps in an adapter that POSTs the AGS score; nothing above the port changes.
 */
@Component
public class InMemoryLtiPlatform implements LtiPlatform {

    public record Passback(String lineItemUrl, String subject, int scorePercent) {
    }

    private final List<Passback> sent = new ArrayList<>();

    @Override
    public synchronized void sendGrade(String lineItemUrl, String subject, int scorePercent) {
        if (scorePercent < 0 || scorePercent > 100) {
            throw new IllegalArgumentException("score must be 0..100");
        }
        sent.add(new Passback(lineItemUrl, subject, scorePercent));
    }

    /** Test/observability helper: the passbacks recorded so far. */
    public synchronized List<Passback> sent() {
        return List.copyOf(sent);
    }
}
