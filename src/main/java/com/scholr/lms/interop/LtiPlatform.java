package com.scholr.lms.interop;

/**
 * The seam for calling <em>back</em> into the LTI platform — chiefly AGS (Assignment and Grade Services)
 * grade passback, where the tool reports a score to the institution's gradebook. A real implementation
 * POSTs an AGS score to the {@code lineItemUrl} from the launch, authenticated with a client-credentials
 * token. The in-memory implementation records the passback so tests can assert it happened.
 */
public interface LtiPlatform {

    /** Send a score (0–100) for a learner back to the platform's gradebook at the given AGS line-item URL. */
    void sendGrade(String lineItemUrl, String subject, int scorePercent);
}
