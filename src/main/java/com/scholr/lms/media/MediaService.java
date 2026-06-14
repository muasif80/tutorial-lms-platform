package com.scholr.lms.media;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.media.domain.TranscodeJob;
import com.scholr.lms.media.domain.VideoAsset;
import com.scholr.lms.media.domain.VideoRendition;
import com.scholr.lms.media.internal.TranscodeJobRepository;
import com.scholr.lms.media.internal.VideoAssetRepository;
import com.scholr.lms.media.internal.VideoRenditionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the Media context: register an uploaded video, kick off transcoding
 * (asynchronously and idempotently), record the renditions the pipeline produced, and
 * issue a signed playback URL. Every query here is tenant-scoped by Hibernate.
 *
 * <p>The design constraint that shapes this class is that transcoding is slow and expensive,
 * so it must never run on the request path and must never run twice for one upload. Both are
 * solved with the same primitives the rest of the platform uses: persist a job row and return
 * immediately (async), and key that row on an idempotency key (find-or-create).
 */
@Service
public class MediaService {

    /**
     * The ABR ladder Scholr packages for every asset. Per-title encoding in a real system
     * would tune these, but a fixed ladder is the right starting point and keeps the bill
     * predictable. Kept here so both the enqueue and the (simulated) transcode agree on it.
     */
    private static final int[][] DEFAULT_LADDER = {
        {360, 800},
        {480, 1400},
        {720, 2800},
        {1080, 5000}
    };

    private final VideoAssetRepository assets;
    private final VideoRenditionRepository renditions;
    private final TranscodeJobRepository jobs;
    private final SignedUrlIssuer urlIssuer;

    public MediaService(VideoAssetRepository assets, VideoRenditionRepository renditions,
                        TranscodeJobRepository jobs, SignedUrlIssuer urlIssuer) {
        this.assets = assets;
        this.renditions = renditions;
        this.jobs = jobs;
        this.urlIssuer = urlIssuer;
    }

    /**
     * Register a freshly uploaded source video. The bytes are already in object storage
     * (uploaded directly via a presigned URL, never through this app), so all we persist is
     * the metadata: the course it belongs to and the source object key.
     */
    @Transactional
    public VideoAsset registerUpload(UUID courseId, String sourceKey) {
        return assets.save(VideoAsset.uploaded(courseId, sourceKey));
    }

    /**
     * The current tenant's video assets for a course — a tenant-scoped query (Hibernate
     * {@code @TenantId} adds the filter automatically). A different tenant asking for the
     * same course id gets an empty list, never another tenant's videos.
     */
    @Transactional(readOnly = true)
    public List<VideoAsset> assetsForCourse(UUID courseId) {
        return assets.findByCourseId(courseId);
    }

    /**
     * Enqueue transcoding for an asset — <em>off the request path</em> and idempotent.
     *
     * <p>This method only writes a job row and flips the asset to {@code TRANSCODING}; it does
     * not transcode. A worker (or a managed transcoder) drains the queue out of band. Calling it
     * twice for the same upload — a duplicated upload callback, a client retry — returns the
     * existing job instead of paying for a second transcode, because the job is keyed on a
     * per-tenant idempotency key. This is the Part 2 idempotent-enroll pattern applied to an
     * expensive side effect.
     */
    @Transactional
    public TranscodeJob enqueueTranscode(UUID assetId, String idempotencyKey) {
        return jobs.findByIdempotencyKey(idempotencyKey)
            .orElseGet(() -> {
                VideoAsset asset = loadAsset(assetId);
                asset.markTranscoding();
                assets.save(asset);
                return jobs.save(TranscodeJob.queue(assetId, idempotencyKey));
            });
    }

    /**
     * Record the renditions a completed transcode produced and mark the asset streamable.
     * This is what a worker calls after FFmpeg/the transcoder finishes. It is idempotent at
     * the database level — a {@code (tenant_id, asset_id, height)} unique constraint means a
     * transcoder that delivers "done" twice (at-least-once delivery is the norm) cannot create
     * duplicate rungs, and {@code markReady()} is itself idempotent.
     */
    @Transactional
    public VideoAsset completeTranscode(UUID assetId, UUID jobId) {
        VideoAsset asset = loadAsset(assetId);
        for (int[] rung : DEFAULT_LADDER) {
            int height = rung[0];
            int bitrate = rung[1];
            boolean exists = renditions.findByAssetIdOrderByHeightAsc(assetId).stream()
                .anyMatch(r -> r.height() == height);
            if (!exists) {
                String playlistKey = "renditions/" + assetId + "/" + height + "p/index.m3u8";
                renditions.save(VideoRendition.of(assetId, height, bitrate, playlistKey));
            }
        }
        asset.markReady();
        jobs.findById(jobId).ifPresent(job -> {
            job.complete();
            jobs.save(job);
        });
        return assets.save(asset);
    }

    /** The ABR rungs available for an asset (lowest to highest). */
    @Transactional(readOnly = true)
    public List<VideoRendition> renditionsFor(UUID assetId) {
        return renditions.findByAssetIdOrderByHeightAsc(assetId);
    }

    /**
     * Issue a short-lived, signed CDN URL for the asset's master HLS playlist — the only thing
     * the player needs. Throws if the asset isn't {@code READY}, so a learner can never be handed
     * a URL to a half-transcoded asset. Issuing the URL never touches the transcoding path; it's
     * a cheap, hot-path operation, which is exactly why signed URLs scale.
     */
    @Transactional(readOnly = true)
    public String playbackUrl(UUID assetId) {
        VideoAsset asset = loadAsset(assetId);
        if (!asset.isStreamable()) {
            throw new IllegalStateException("asset " + assetId + " is not ready: " + asset.status());
        }
        String masterPlaylistKey = "renditions/" + assetId + "/master.m3u8";
        return urlIssuer.sign(masterPlaylistKey);
    }

    private VideoAsset loadAsset(UUID assetId) {
        return assets.findById(assetId)
            .orElseThrow(() -> new IllegalArgumentException("asset not found: " + assetId));
    }
}
