package com.scholr.lms.enrollment.domain;

import java.util.UUID;

/** Thrown when an enrollment would violate the cohort's seat invariant. */
public class CohortFullException extends RuntimeException {

    public CohortFullException(UUID cohortId) {
        super("Cohort " + cohortId + " is full");
    }
}
