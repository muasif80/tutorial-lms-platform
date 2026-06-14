package com.scholr.lms.media.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

/**
 * The Media aggregate root: one uploaded source video and its lifecycle. Tenant-scoped.
 *
 * <p>An asset is created the moment bytes land in object storage ({@link AssetStatus#UPLOADED}),
 * then moves through {@code TRANSCODING} to {@code READY} as the pipeline packages its
 * adaptive-bitrate renditions. The transition is guarded by a {@code @Version} optimistic
 * lock so two concurrent callbacks (a transcoder is allowed to retry and deliver "done"
 * twice) cannot both flip the status and double-process the asset.
 *
 * <p>Following the series rule, the asset references its renditions and jobs <em>by id</em>,
 * not by JPA association — those are separate rows the {@code MediaService} stitches together,
 * which keeps the aggregate small and the write transaction cheap.
 */
@Entity
@Table(name = "video_assets")
public class VideoAsset {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The catalog course this video belongs to (reference across aggregates by id). */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** The storage key of the original upload in the object store (e.g. an S3 object key). */
    @Column(name = "source_key", nullable = false, updatable = false)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    @Version
    private long version;

    protected VideoAsset() {
    }

    public VideoAsset(UUID id, UUID courseId, String sourceKey) {
        this.id = id;
        this.courseId = courseId;
        this.sourceKey = sourceKey;
        this.status = AssetStatus.UPLOADED;
    }

    /** A freshly uploaded asset, awaiting transcoding. */
    public static VideoAsset uploaded(UUID courseId, String sourceKey) {
        return new VideoAsset(UUID.randomUUID(), courseId, sourceKey);
    }

    /** Move into transcoding. Idempotent: re-calling once already transcoding is a no-op. */
    public void markTranscoding() {
        if (status == AssetStatus.UPLOADED || status == AssetStatus.FAILED) {
            status = AssetStatus.TRANSCODING;
        }
    }

    /**
     * Renditions are packaged and the asset is streamable. Idempotent so a transcoder
     * that delivers "done" twice (at-least-once callbacks are the norm) cannot corrupt state.
     */
    public void markReady() {
        status = AssetStatus.READY;
    }

    public void markFailed() {
        status = AssetStatus.FAILED;
    }

    public boolean isStreamable() {
        return status == AssetStatus.READY;
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

    public String sourceKey() {
        return sourceKey;
    }

    public AssetStatus status() {
        return status;
    }
}
