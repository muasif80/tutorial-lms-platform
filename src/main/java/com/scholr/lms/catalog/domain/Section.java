package com.scholr.lms.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * Part 15: a section of a lesson — the unit of rich, block-authored content. A lesson is an ordered list
 * of sections; each section is authored in a block editor (Editor.js) and stored as two things:
 * <ul>
 *   <li>{@code contentJson} — the editor's block model, the editable source of truth; and</li>
 *   <li>{@code renderedHtml} — the server-rendered, <em>sanitized</em> HTML shown to learners, so the
 *       student view needs no JavaScript and a stored-XSS payload can never reach a reader.</li>
 * </ul>
 * Tenant-scoped (@TenantId) and isolated like every other table; references its lesson and course by id.
 */
@Entity
@Table(name = "sections")
public class Section {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    // 'text' in the migration (Types.VARCHAR). A generous length keeps the test schema (H2 create-drop)
    // roomy; Hibernate's validate checks the type code, not the length, so this matches the Postgres 'text'.
    @Column(name = "content_json", length = 500_000)
    private String contentJson;

    @Column(name = "rendered_html", length = 500_000)
    private String renderedHtml;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Section() {
    }

    public Section(UUID id, UUID courseId, UUID lessonId, String title, int position,
                   String contentJson, String renderedHtml, Instant updatedAt) {
        this.id = id;
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.title = title;
        this.position = position;
        this.contentJson = contentJson;
        this.renderedHtml = renderedHtml;
        this.updatedAt = updatedAt;
    }

    public static Section of(UUID courseId, UUID lessonId, String title, int position, Instant now) {
        return new Section(UUID.randomUUID(), courseId, lessonId, title, position, "", "", now);
    }

    /** Save edited content: the block JSON (source) and its sanitized rendered HTML (for display). */
    public void edit(String title, String contentJson, String renderedHtml, Instant now) {
        this.title = title;
        this.contentJson = contentJson;
        this.renderedHtml = renderedHtml;
        this.updatedAt = now;
    }

    public void moveTo(int position) {
        this.position = position;
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

    public UUID lessonId() {
        return lessonId;
    }

    public String title() {
        return title;
    }

    public int position() {
        return position;
    }

    public String contentJson() {
        return contentJson;
    }

    public String renderedHtml() {
        return renderedHtml;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
