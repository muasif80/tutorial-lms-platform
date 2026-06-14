package com.scholr.lms.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.domain.GradedResult;
import com.scholr.lms.assessment.domain.Question;
import com.scholr.lms.assessment.domain.QuestionType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic grading engine — no Spring, no database, no clock. These prove
 * the engine is pure arithmetic: same inputs, same score, every time. If a learner disputes a
 * grade, this is the spec that settles it.
 */
class AutoGraderTest {

    private final AutoGrader grader = new AutoGrader();

    private static Question single(UUID assessment, int points, List<String> options, String correctIdx) {
        return Question.of(assessment, QuestionType.SINGLE_CHOICE, "q", points, options, Set.of(correctIdx));
    }

    @Test
    void single_choice_is_all_or_nothing() {
        UUID a = UUID.randomUUID();
        Question q = single(a, 5, List.of("A", "B", "C"), "1");

        assertEquals(5, grader.scoreOne(q, Set.of("1")), "correct option earns full points");
        assertEquals(0, grader.scoreOne(q, Set.of("0")), "wrong option earns zero");
        assertEquals(0, grader.scoreOne(q, Set.of()), "no answer earns zero");
        assertEquals(0, grader.scoreOne(q, Set.of("0", "1")), "selecting two on a single-choice earns zero");
    }

    @Test
    void multiple_choice_gives_proportional_partial_credit_floored_at_zero() {
        UUID a = UUID.randomUUID();
        // 8 points, options A B C D, correct = A and C.
        Question q = Question.of(a, QuestionType.MULTIPLE_CHOICE, "q", 8,
            List.of("A", "B", "C", "D"), Set.of("0", "2"));

        assertEquals(8, grader.scoreOne(q, Set.of("0", "2")), "both correct earns full marks");
        assertEquals(4, grader.scoreOne(q, Set.of("0")), "one of two correct earns half");
        // one correct (+4) and one wrong (-4) -> 0
        assertEquals(0, grader.scoreOne(q, Set.of("0", "1")), "a wrong pick cancels a right pick");
        // two wrong picks would be -8, floored at 0 (never negative)
        assertEquals(0, grader.scoreOne(q, Set.of("1", "3")), "all-wrong is floored at zero, never negative");
        assertEquals(0, grader.scoreOne(q, Set.of()), "no selection earns zero");
    }

    @Test
    void short_text_matches_after_normalization_only() {
        UUID a = UUID.randomUUID();
        Question q = Question.of(a, QuestionType.SHORT_TEXT, "capital of France?", 3,
            List.of(), Set.of("Paris", "paris france"));

        assertEquals(3, grader.scoreOne(q, Set.of("Paris")), "exact accepted answer");
        assertEquals(3, grader.scoreOne(q, Set.of("  paris  ")), "trim + case are normalized away");
        assertEquals(3, grader.scoreOne(q, Set.of("PARIS   FRANCE")), "collapsed whitespace + case match");
        assertEquals(0, grader.scoreOne(q, Set.of("London")), "a wrong answer earns zero");
        assertEquals(0, grader.scoreOne(q, Set.of()), "blank earns zero");
    }

    @Test
    void grading_a_whole_submission_sums_per_question() {
        UUID a = UUID.randomUUID();
        Question q1 = single(a, 5, List.of("A", "B"), "0");
        Question q2 = Question.of(a, QuestionType.MULTIPLE_CHOICE, "q2", 4,
            List.of("A", "B"), Set.of("0", "1"));
        Question q3 = Question.of(a, QuestionType.SHORT_TEXT, "q3", 1, List.of(), Set.of("yes"));

        Map<UUID, Set<String>> answers = Map.of(
            q1.id(), Set.of("0"),           // 5/5
            q2.id(), Set.of("0"),           // 2/4 (half)
            q3.id(), Set.of("YES")          // 1/1
        );
        GradedResult result = grader.grade(List.of(q1, q2, q3), answers);
        assertEquals(8, result.score());
        assertEquals(10, result.maxScore());
        assertEquals(80.0, result.percentage(), 0.0001);
    }

    @Test
    void graded_result_rejects_impossible_scores() {
        assertThrows(IllegalArgumentException.class, () -> new GradedResult(5, 4));
        assertThrows(IllegalArgumentException.class, () -> new GradedResult(-1, 10));
    }
}
