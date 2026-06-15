package com.scholr.lms.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * Part 15 (demo): a small tenant-scoped store for images uploaded from the lesson block editor, served back
 * by id. The bytes are kept base64-encoded in a text column — deliberately simple, so the working demo is
 * self-contained (no object storage, and no Postgres {@code bytea}/{@code oid} mapping subtleties under
 * Hibernate's schema validation).
 *
 * <p><strong>In production this is not where bytes live.</strong> Part 3 established the rule that large
 * media bytes never touch the application server: uploads go straight to object storage via a presigned URL
 * and play back through a signed CDN URL. This is the demo stand-in for that pipeline, limited to small
 * images. Video, audio, PDFs and documents are <em>referenced by URL/embed</em>, never uploaded here.
 */
@Entity
@Table(name = "media_blobs")
public class MediaBlob {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String filename;

    /** base64-encoded image bytes, stored in a 'text' column. */
    @Column(name = "data_b64", nullable = false, length = 20_000_000)
    private String dataB64;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaBlob() {
    }

    public MediaBlob(UUID id, String contentType, String filename, String dataB64, Instant createdAt) {
        this.id = id;
        this.contentType = contentType;
        this.filename = filename;
        this.dataB64 = dataB64;
        this.createdAt = createdAt;
    }

    public static MediaBlob of(String contentType, String filename, String dataB64, Instant now) {
        return new MediaBlob(UUID.randomUUID(), contentType, filename, dataB64, now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String contentType() {
        return contentType;
    }

    public String filename() {
        return filename;
    }

    public String dataB64() {
        return dataB64;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
