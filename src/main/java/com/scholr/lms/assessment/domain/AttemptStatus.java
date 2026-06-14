package com.scholr.lms.assessment.domain;

/** Lifecycle of a single learner attempt at an assessment. */
public enum AttemptStatus {

    /** Started, the clock is running, not yet submitted. */
    IN_PROGRESS,

    /** Submitted and graded. Terminal. */
    SUBMITTED,

    /** The time limit elapsed before submission; graded on whatever was saved. Terminal. */
    EXPIRED
}
