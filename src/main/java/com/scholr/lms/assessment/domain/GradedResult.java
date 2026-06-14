package com.scholr.lms.assessment.domain;

/**
 * The outcome of grading: points earned out of points possible. A value object — immutable,
 * comparable, and trivially serializable. {@code percentage} is derived, never stored, so it
 * can never drift from {@code score}/{@code maxScore}.
 */
public record GradedResult(int score, int maxScore) {

    public GradedResult {
        if (score < 0 || maxScore < 0 || score > maxScore) {
            throw new IllegalArgumentException("invalid grade: " + score + "/" + maxScore);
        }
    }

    public double percentage() {
        return maxScore == 0 ? 0.0 : (score * 100.0) / maxScore;
    }

    public boolean isPass(double threshold) {
        return percentage() >= threshold;
    }
}
