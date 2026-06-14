/**
 * Assessment bounded context: quizzes and exams, their question banks, learner attempts, and a
 * deterministic auto-grading engine. Implemented in Part 4.
 *
 * <p>It owns the rules that must be exactly right — the attempts policy, time limits, and
 * exactly-once submission — and computes scores by arithmetic, not judgment, through the pure
 * {@link com.scholr.lms.assessment.AutoGrader}. Submission is idempotent (the same discipline as
 * Part 2's enroll): a duplicate submit returns the already-recorded grade rather than regrading.
 *
 * <p>Like every context it is tenant-scoped and self-contained: it depends only on the shared
 * kernel ({@code shared}) and references the catalog course by id, never by a cross-module
 * association.
 */
package com.scholr.lms.assessment;
