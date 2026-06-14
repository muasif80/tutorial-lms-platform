package com.scholr.lms.media.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/**
 * A unit of work for the transcoding pipeline: "turn this asset's source into the ABR ladder".
 * Tenant-scoped. The job is the record that lets transcoding run <em>off the request path</em> —
 * the upload handler returns immediately after persisting one of these, and a worker drains the
 * queue asynchronously.
 *
 * <p>Idempotency lives in the {@code idempotency_key} (unique per tenant): an upload callback
 * that fires twice, or a client that retries on a dropped connection, resolves to the same job
 * row instead of spawning a second (expensive) transcode. This mirrors the idempotent-enroll
 * pattern from Part 2 — find-or-create keyed on a natural unique constraint.
 */
@Entity
@Table(
    name = "transcode_jobs",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"})
)
public class TranscodeJob {

    /** State machine for the job itself (distinct from the asset's lifecycle). */
    public enum State {
        QUEUED,
        RUNNING,
        DONE,
        FAILED
    }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    /** Natural dedup key, unique per tenant — makes enqueue idempotent under retries. */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    /** How many times a worker has picked this job up; used to cap retries. */
    @Column(nullable = false)
    private int attempts;

    protected TranscodeJob() {
    }

    public TranscodeJob(UUID id, UUID assetId, String idempotencyKey) {
        this.id = id;
        this.assetId = assetId;
        this.idempotencyKey = idempotencyKey;
        this.state = State.QUEUED;
        this.attempts = 0;
    }

    public static TranscodeJob queue(UUID assetId, String idempotencyKey) {
        return new TranscodeJob(UUID.randomUUID(), assetId, idempotencyKey);
    }

    public void start() {
        this.state = State.RUNNING;
        this.attempts++;
    }

    public void complete() {
        this.state = State.DONE;
    }

    public void fail() {
        this.state = State.FAILED;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID assetId() {
        return assetId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public State state() {
        return state;
    }

    public int attempts() {
        return attempts;
    }
}
