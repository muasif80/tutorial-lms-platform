package com.scholr.lms.assessment.domain;

/**
 * The kinds of question Scholr can grade <em>deterministically</em> — by arithmetic, not
 * judgment. This is the boundary the article draws: a machine should grade these and only
 * these. Anything requiring interpretation (an essay) is routed to a human or, later, to a
 * judgment model — but it never pretends to be a deterministic score.
 */
public enum QuestionType {

    /** Exactly one correct option. */
    SINGLE_CHOICE,

    /** Any number of correct options; partial credit is possible. */
    MULTIPLE_CHOICE,

    /** A free-text answer matched against accepted values (case/whitespace-normalized). */
    SHORT_TEXT
}
