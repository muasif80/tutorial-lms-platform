package com.scholr.lms.web;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.media.MediaService;
import com.scholr.lms.media.domain.TranscodeJob;
import com.scholr.lms.media.domain.VideoAsset;
import com.scholr.lms.media.domain.VideoRendition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the Media context. Note what is <em>not</em> here: the video bytes never
 * pass through this controller. Uploads go directly to object storage via a presigned URL,
 * and playback bytes come from the CDN via a signed URL — the app only handles small JSON.
 * That is the whole point of the architecture: keep multi-gigabyte media off the app servers.
 */
@RestController
@RequestMapping("/api")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    public record RegisterUpload(UUID courseId, String sourceKey) {
    }

    public record AssetView(UUID id, UUID courseId, String status) {
    }

    private static AssetView toView(VideoAsset asset) {
        return new AssetView(asset.id(), asset.courseId(), asset.status().name());
    }

    /** Called after the client has uploaded directly to object storage. Persists metadata only. */
    @PostMapping("/videos")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView registerUpload(@RequestBody RegisterUpload request) {
        return toView(media.registerUpload(request.courseId(), request.sourceKey()));
    }

    public record Enqueue(String idempotencyKey) {
    }

    public record JobView(UUID id, UUID assetId, String state) {
    }

    /** Kick off transcoding off the request path; idempotent on the supplied key. */
    @PostMapping("/videos/{assetId}/transcode")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobView enqueueTranscode(@PathVariable UUID assetId, @RequestBody Enqueue request) {
        TranscodeJob job = media.enqueueTranscode(assetId, request.idempotencyKey());
        return new JobView(job.id(), job.assetId(), job.state().name());
    }

    public record RenditionView(int height, int bitrateKbps) {
    }

    @GetMapping("/videos/{assetId}/renditions")
    public List<RenditionView> renditions(@PathVariable UUID assetId) {
        return media.renditionsFor(assetId).stream()
            .map(r -> new RenditionView(r.height(), r.bitrateKbps()))
            .toList();
    }

    public record PlaybackView(String url) {
    }

    /** Returns a short-lived signed CDN URL for the master HLS playlist. */
    @GetMapping("/videos/{assetId}/playback")
    public PlaybackView playback(@PathVariable UUID assetId) {
        return new PlaybackView(media.playbackUrl(assetId));
    }
}
