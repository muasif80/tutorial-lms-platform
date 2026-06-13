package com.scholr.lms.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/** Catalog context: the versioned definition of learning content. Tenant-scoped. */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private boolean published;

    protected Course() {
    }

    public Course(UUID id, String title, boolean published) {
        this.id = id;
        this.title = title;
        this.published = published;
    }

    public static Course create(String title) {
        return new Course(UUID.randomUUID(), title, false);
    }

    public void publish() {
        this.published = true;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String title() {
        return title;
    }

    public boolean isPublished() {
        return published;
    }
}
