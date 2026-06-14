package com.scholr.lms.assessment.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * One gradable question in an assessment's bank. Tenant-scoped.
 *
 * <p>A question carries everything the {@link com.scholr.lms.assessment.AutoGrader} needs to
 * score an answer with zero ambiguity: the type, the points it is worth, the option labels,
 * and — crucially — the <em>correct</em> answer key. The key lives on the server and is never
 * serialized to the learner; the grader is the only thing that reads it.
 *
 * <p>Following the series rule, a question references its assessment <em>by id</em>, not by a
 * JPA association, so the bank can be loaded a page at a time without dragging the aggregate.
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private int points;

    /** Option labels (for choice questions); empty for SHORT_TEXT. */
    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "label")
    private List<String> options;

    /**
     * The correct answer key. For choice questions these are the indices of the correct
     * options (as strings, e.g. "0","2"); for SHORT_TEXT they are the accepted answers.
     * Server-side only — never sent to the client.
     */
    @ElementCollection
    @CollectionTable(name = "question_answer_key", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "value")
    private Set<String> answerKey;

    protected Question() {
    }

    public Question(UUID id, UUID assessmentId, QuestionType type, String prompt, int points,
                    List<String> options, Set<String> answerKey) {
        if (points < 0) {
            throw new IllegalArgumentException("points must be >= 0");
        }
        this.id = id;
        this.assessmentId = assessmentId;
        this.type = type;
        this.prompt = prompt;
        this.points = points;
        this.options = options;
        this.answerKey = answerKey;
    }

    public static Question of(UUID assessmentId, QuestionType type, String prompt, int points,
                              List<String> options, Set<String> answerKey) {
        return new Question(UUID.randomUUID(), assessmentId, type, prompt, points, options, answerKey);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID assessmentId() {
        return assessmentId;
    }

    public QuestionType type() {
        return type;
    }

    public String prompt() {
        return prompt;
    }

    public int points() {
        return points;
    }

    public List<String> options() {
        return options;
    }

    public Set<String> answerKey() {
        return answerKey;
    }
}
