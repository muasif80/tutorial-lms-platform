package com.scholr.lms.media;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mints short-lived, signed playback URLs for HLS playlists served from the CDN.
 *
 * <p>This is the access-control boundary for paid video. The object store and CDN are
 * private; the only way to fetch a playlist or segment is with a URL this issuer signed,
 * and the signature is bound to the path and an expiry. A leaked URL is therefore useless
 * within minutes, and a learner cannot fabricate a URL for content they didn't pay for —
 * they'd need the signing key, which never leaves the server. That makes hot-linking and
 * URL sharing <em>structurally</em> limited rather than merely discouraged.
 *
 * <p>The mechanism here (HMAC over {@code path + expiry}) is the same one every CDN's
 * signed-URL feature implements; in production you'd hand this off to the CDN's own signer
 * (CloudFront/Cloudflare/Akamai), but the contract — a tenant-scoped, time-boxed, tamper-proof
 * URL minted off the hot path — is identical. Issuing a URL touches no database.
 */
@Component
public class SignedUrlIssuer {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final String cdnBaseUrl;
    private final byte[] signingKey;
    private final Duration ttl;
    private final Clock clock;

    public SignedUrlIssuer(
            @Value("${media.cdn.base-url:https://cdn.scholr.example}") String cdnBaseUrl,
            @Value("${media.cdn.signing-key:scholr-dev-signing-key-change-me}") String signingKey,
            @Value("${media.cdn.url-ttl-seconds:300}") long ttlSeconds) {
        this(cdnBaseUrl, signingKey, Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
    }

    /** Test/seam constructor: inject a fixed clock so expiries are deterministic. */
    public SignedUrlIssuer(String cdnBaseUrl, String signingKey, Duration ttl, Clock clock) {
        this.cdnBaseUrl = cdnBaseUrl.endsWith("/") ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1) : cdnBaseUrl;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Returns a CDN URL for the given storage key, signed and valid for the configured TTL.
     * The expiry and signature are query params the CDN edge validates before serving a byte.
     */
    public String sign(String storageKey) {
        String path = storageKey.startsWith("/") ? storageKey : "/" + storageKey;
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        String signature = hmac(path + ":" + expiresAt);
        return cdnBaseUrl + path + "?expires=" + expiresAt + "&signature=" + signature;
    }

    /**
     * Verifies a previously issued signature for a path+expiry — the check a CDN edge worker
     * (or an origin shield) would run. Returns false on tamper or expiry. Exposed so the
     * test suite can prove the URL is genuinely tamper-proof, not just opaque.
     */
    public boolean isValid(String path, long expiresAt, String signature) {
        if (clock.instant().getEpochSecond() > expiresAt) {
            return false;
        }
        String expected = hmac(path + ":" + expiresAt);
        return constantTimeEquals(expected, signature);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign url", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }
}
