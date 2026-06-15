package com.scholr.lms.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * One lesson in a course — the unit an instructor authors and a learner consumes. Tenant-scoped, and
 * referenced to its course by id (the series rule), with an explicit {@code position} so the instructor
 * controls ordering. Kept deliberately small: a title, an ordinal, and a body of text content. A richer
 * system would model typed blocks (video, quiz, embed) here, exactly as Part 9's authoring discussion
 * describes; this is the smallest model that makes course authoring real.
 */
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    @Column(length = 4000)
    private String body;

    protected Lesson() {
    }

    public Lesson(UUID id, UUID courseId, String title, int position, String body) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.position = position;
        this.body = body;
    }

    public static Lesson of(UUID courseId, String title, int position, String body) {
        return new Lesson(UUID.randomUUID(), courseId, title, position, body);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID courseId() {
        return courseId;
    }

    public String title() {
        return title;
    }

    public int position() {
        return position;
    }

    public String body() {
        return body;
    }
}
