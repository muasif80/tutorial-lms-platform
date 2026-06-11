package com.scholr.lms.enrollment.domain;

/** A learner's enrollment into a cohort. */
public record Enrollment(CohortId cohort, LearnerId learner) {
}
