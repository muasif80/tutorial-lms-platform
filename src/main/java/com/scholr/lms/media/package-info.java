/**
 * Media bounded context: course video at scale. Owns the lifecycle of an uploaded
 * source video — registration (bytes land in object storage via a presigned URL),
 * asynchronous and idempotent transcoding into an adaptive-bitrate (HLS) ladder, and
 * the issuance of short-lived signed CDN URLs for playback. Implemented in Part 3.
 *
 * <p>Like every context it is tenant-scoped and self-contained: it depends only on the
 * shared kernel ({@code shared}) and references the catalog course by id, never by a
 * cross-module association. The expensive work (transcoding) runs off the request path.
 */
package com.scholr.lms.media;
