package com.scholr.lms.assessment.domain;

import java.util.UUID;

/**
 * Thrown when an attempt would violate the assessment's policy — for example starting one more
 * attempt than {@code maxAttempts} allows. A domain exception, so the rule lives with the model
 * rather than in a controller.
 */
public class AttemptPolicyException extends RuntimeException {

    public AttemptPolicyException(UUID assessmentId, String reason) {
        super("attempt policy violated for assessment " + assessmentId + ": " + reason);
    }
}
