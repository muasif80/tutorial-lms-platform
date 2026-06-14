package com.scholr.lms.media.domain;

/**
 * Lifecycle of a {@link VideoAsset}. A learner may only stream an asset that has
 * reached {@link #READY}; everything before that is the pipeline doing its work,
 * and {@link #FAILED} is a terminal error state a human (or a retry) must clear.
 *
 * <pre>
 *   UPLOADED ──enqueue──▶ TRANSCODING ──renditions packaged──▶ READY
 *                              │
 *                              └────────── job failed ────────▶ FAILED
 * </pre>
 */
public enum AssetStatus {

    /** Bytes have landed in object storage; no renditions exist yet. */
    UPLOADED,

    /** A transcode job is in flight (off the request path). */
    TRANSCODING,

    /** At least one playable rendition exists; the asset is streamable. */
    READY,

    /** Transcoding failed terminally; needs intervention or a fresh job. */
    FAILED
}
