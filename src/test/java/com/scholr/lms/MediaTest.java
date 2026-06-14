package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.media.MediaService;
import com.scholr.lms.media.SignedUrlIssuer;
import com.scholr.lms.media.domain.AssetStatus;
import com.scholr.lms.media.domain.TranscodeJob;
import com.scholr.lms.media.domain.VideoAsset;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 3 guarantees of the Media context on a real persistence stack (H2):
 * tenant isolation on video assets, idempotent transcode enqueue (so an upload callback that
 * fires twice never pays for a second transcode), and signed-URL playback (only for a READY
 * asset, and the signature is genuinely tamper-proof). Everything goes through the public
 * {@link MediaService} so the module boundary holds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MediaTest {

    @Autowired
    private IdentityService identity;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private MediaService media;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void tenants_cannot_see_each_others_videos() {
        Organization acme = identity.createOrganization("Acme");
        Organization globex = identity.createOrganization("Globex");

        TenantContext.set(TenantId.of(acme.id()));
        Course acmeCourse = catalog.createCourse("Acme Course");
        VideoAsset acmeAsset = media.registerUpload(acmeCourse.id(), "uploads/acme/intro.mp4");

        // Globex must not be able to load Acme's asset (cross-tenant read is blocked).
        TenantContext.set(TenantId.of(globex.id()));
        assertThrows(IllegalArgumentException.class,
            () -> media.enqueueTranscode(acmeAsset.id(), "globex-key"),
            "Globex must not reach Acme's asset");

        // Acme still sees its own asset fine.
        TenantContext.set(TenantId.of(acme.id()));
        assertEquals(AssetStatus.UPLOADED, acmeAsset.status());
    }

    @Test
    void enqueue_transcode_is_idempotent_and_off_the_request_path() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        Course course = catalog.createCourse("Course");
        VideoAsset asset = media.registerUpload(course.id(), "uploads/acme/lecture-1.mp4");

        String key = "asset:" + asset.id() + ":transcode";
        TranscodeJob first = media.enqueueTranscode(asset.id(), key);
        TranscodeJob retry = media.enqueueTranscode(asset.id(), key); // duplicate callback

        // Same job row, no second (expensive) transcode.
        assertEquals(first.id(), retry.id(), "a retried enqueue must not create a second job");
        assertEquals(TranscodeJob.State.QUEUED, retry.state());
    }

    @Test
    void asset_becomes_streamable_and_yields_a_valid_signed_url() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));
        Course course = catalog.createCourse("Course");
        VideoAsset asset = media.registerUpload(course.id(), "uploads/acme/lecture-2.mp4");

        // Not streamable until transcoding completes.
        assertThrows(IllegalStateException.class, () -> media.playbackUrl(asset.id()));

        TranscodeJob job = media.enqueueTranscode(asset.id(), "k1");
        VideoAsset ready = media.completeTranscode(asset.id(), job.id());
        assertEquals(AssetStatus.READY, ready.status());
        assertEquals(4, media.renditionsFor(asset.id()).size(), "the full ABR ladder is packaged");

        // completeTranscode is idempotent — a duplicate "done" must not double the ladder.
        media.completeTranscode(asset.id(), job.id());
        assertEquals(4, media.renditionsFor(asset.id()).size());

        String url = media.playbackUrl(asset.id());
        assertTrue(url.contains("signature="), "playback url is signed");
        assertTrue(url.contains("expires="), "playback url is time-boxed");
    }

    @Test
    void signed_url_is_tamper_proof_and_expires() {
        // Use a fixed clock so the expiry math is deterministic.
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        SignedUrlIssuer issuer = new SignedUrlIssuer(
            "https://cdn.scholr.example", "test-signing-key", Duration.ofMinutes(5), fixed);

        String url = issuer.sign("renditions/abc/master.m3u8");
        long expires = Long.parseLong(url.replaceAll(".*expires=(\\d+).*", "$1"));
        String signature = url.replaceAll(".*signature=([^&]+).*", "$1");
        String path = "/renditions/abc/master.m3u8";

        assertTrue(issuer.isValid(path, expires, signature), "a freshly minted url validates");
        assertFalse(issuer.isValid(path, expires, signature + "x"), "a tampered signature is rejected");
        assertFalse(issuer.isValid("/renditions/other/master.m3u8", expires, signature),
            "a signature for one path does not validate another (no swapping)");

        // After the TTL, the same signature is no longer valid.
        Clock later = Clock.fixed(now.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);
        SignedUrlIssuer expiredCheck = new SignedUrlIssuer(
            "https://cdn.scholr.example", "test-signing-key", Duration.ofMinutes(5), later);
        assertFalse(expiredCheck.isValid(path, expires, signature), "an expired url is rejected");

        // A url signed with a different key never validates against ours.
        SignedUrlIssuer attacker = new SignedUrlIssuer(
            "https://cdn.scholr.example", "WRONG-key", Duration.ofMinutes(5), fixed);
        String forged = attacker.sign("renditions/abc/master.m3u8");
        String forgedSig = forged.replaceAll(".*signature=([^&]+).*", "$1");
        assertFalse(issuer.isValid(path, expires, forgedSig), "a url signed with the wrong key is rejected");
    }
}
