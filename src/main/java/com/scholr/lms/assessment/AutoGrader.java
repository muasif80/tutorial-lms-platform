package com.scholr.lms.assessment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.assessment.domain.QuestionType;

/**
 * The deterministic grading engine. Given a set of questions and a learner's answers, it
 * computes a score by <em>arithmetic, not judgment</em> — the same boundary the production
 * pillar draws for LLMs. It is a pure function: no Spring, no database, no clock, no
 * randomness. Same inputs, same score, every time and on every node. That property is what
 * makes a grade defensible when a learner disputes it, and what makes this class exhaustively
 * unit-testable without a running application.
 *
 * <p>Three grading rules, one per supported {@link QuestionType}:
 * <ul>
 *   <li><b>SINGLE_CHOICE</b> — full points iff the one selected option is the correct one.</li>
 *   <li><b>MULTIPLE_CHOICE</b> — <em>proportional partial credit</em>: each correct option
 *       selected earns a share of the points, each wrong option selected forfeits a share,
 *       floored at zero. No "all or nothing" cliff, no negative totals.</li>
 *   <li><b>SHORT_TEXT</b> — full points iff the normalized answer (trimmed, lower-cased,
 *       internal whitespace collapsed) matches an accepted value. Deterministic string
 *       equality, never fuzzy "AI" matching, so it is explainable and reproducible.</li>
 * </ul>
 *
 * <p>Anything outside these types (an essay) is <em>not graded here</em>. The engine never
 * fabricates a number for something that requires interpretation; such questions are scored
 * by a human or routed to a judgment model as a separate, clearly-labeled step.
 */
public final class AutoGrader {

    /**
     * Grade a whole submission.
     *
     * @param questions the questions in play (the answer key lives here, server-side)
     * @param answers   learner answers keyed by question id; a missing entry scores zero
     * @return the total score out of the total possible points
     */
    public GradedResult grade(List<Question> questions, Map<UUID, Set<String>> answers) {
        int earned = 0;
        int possible = 0;
        for (Question q : questions) {
            possible += q.points();
            Set<String> given = answers.getOrDefault(q.id(), Set.of());
            earned += scoreOne(q, given);
        }
        return new GradedResult(earned, possible);
    }

    /** Score a single question. Package-private so it can be unit-tested in isolation. */
    int scoreOne(Question q, Set<String> given) {
        return switch (q.type()) {
            case SINGLE_CHOICE -> scoreSingleChoice(q, given);
            case MULTIPLE_CHOICE -> scoreMultipleChoice(q, given);
            case SHORT_TEXT -> scoreShortText(q, given);
        };
    }

    private int scoreSingleChoice(Question q, Set<String> given) {
        // Exactly one selection, and it must be the correct one.
        return given.size() == 1 && q.answerKey().containsAll(given) ? q.points() : 0;
    }

    private int scoreMultipleChoice(Question q, Set<String> given) {
        Set<String> key = q.answerKey();
        int totalOptions = q.options().size();
        int wrongOptions = totalOptions - key.size();
        if (key.isEmpty() || totalOptions == 0) {
            return 0;
        }
        long correctSelected = given.stream().filter(key::contains).count();
        long wrongSelected = given.stream().filter(o -> !key.contains(o)).count();

        // Proportional credit: + for each right pick, - for each wrong pick, floored at 0.
        double creditPerCorrect = (double) q.points() / key.size();
        double penaltyPerWrong = wrongOptions == 0 ? 0.0 : (double) q.points() / wrongOptions;
        double raw = correctSelected * creditPerCorrect - wrongSelected * penaltyPerWrong;
        int score = (int) Math.round(Math.max(0.0, raw));
        return Math.min(score, q.points());
    }

    private int scoreShortText(Question q, Set<String> given) {
        if (given.size() != 1) {
            return 0;
        }
        String answer = normalize(given.iterator().next());
        boolean matches = q.answerKey().stream()
            .map(this::normalize)
            .anyMatch(accepted -> accepted.equals(answer));
        return matches ? q.points() : 0;
    }

    /** Trim, lower-case, and collapse internal whitespace so trivial formatting never costs points. */
    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
