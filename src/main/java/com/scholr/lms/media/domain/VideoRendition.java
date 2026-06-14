package com.scholr.lms.media.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/**
 * One rung of the adaptive-bitrate ladder for a {@link VideoAsset} — e.g. 360p@800kbps or
 * 1080p@5Mbps. Tenant-scoped. A rendition points back to its asset by id (references across
 * aggregates are by id, never by JPA association), and carries the storage key of its HLS
 * playlist so the {@code SignedUrlIssuer} can mint a time-boxed playback URL for it.
 *
 * <p>The {@code (tenant_id, asset_id, height)} tuple is unique, which is what makes "package
 * the 720p rendition" idempotent at the database level: a retried transcode job cannot create
 * two 720p rows — the same defense the Part 2 {@code Enrollment} uses for idempotent enroll.
 */
@Entity
@Table(
    name = "video_renditions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "asset_id", "height"})
)
public class VideoRendition {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    /** Vertical resolution (e.g. 360, 720, 1080) — the rung of the ABR ladder. */
    @Column(nullable = false)
    private int height;

    /** Target average bitrate in kbps; the player picks a rung against the live bandwidth. */
    @Column(name = "bitrate_kbps", nullable = false)
    private int bitrateKbps;

    /** Storage key of this rendition's HLS media playlist (the .m3u8). */
    @Column(name = "playlist_key", nullable = false)
    private String playlistKey;

    protected VideoRendition() {
    }

    public VideoRendition(UUID id, UUID assetId, int height, int bitrateKbps, String playlistKey) {
        if (height < 1 || bitrateKbps < 1) {
            throw new IllegalArgumentException("height and bitrate must be positive");
        }
        this.id = id;
        this.assetId = assetId;
        this.height = height;
        this.bitrateKbps = bitrateKbps;
        this.playlistKey = playlistKey;
    }

    public static VideoRendition of(UUID assetId, int height, int bitrateKbps, String playlistKey) {
        return new VideoRendition(UUID.randomUUID(), assetId, height, bitrateKbps, playlistKey);
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

    public int height() {
        return height;
    }

    public int bitrateKbps() {
        return bitrateKbps;
    }

    public String playlistKey() {
        return playlistKey;
    }
}
