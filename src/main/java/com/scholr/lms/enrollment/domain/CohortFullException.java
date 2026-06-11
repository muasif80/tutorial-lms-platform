package com.scholr.lms.enrollment.domain;

/** Thrown when an enrollment would violate the cohort's seat invariant. */
public class CohortFullException extends RuntimeException {

    public CohortFullException(CohortId id) {
        super("Cohort " + id.value() + " is full");
    }
}
