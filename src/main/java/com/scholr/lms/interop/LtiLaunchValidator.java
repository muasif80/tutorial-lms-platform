package com.scholr.lms.interop;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.scholr.lms.interop.domain.LtiClaims;
import org.springframework.stereotype.Component;

/**
 * Validates an LTI 1.3 launch token and extracts its {@link LtiClaims}. A real LTI launch token is an
 * RS256-signed JWT whose signature is verified against the platform's public keys (fetched from its JWKS
 * endpoint), with {@code iss}/{@code aud}/{@code nonce}/{@code exp} all checked. Here the signature is an
 * HMAC over the claims — a deterministic stand-in so the security-critical <em>logic</em> (reject a tampered
 * or expired token; trust only a correctly-signed one) is real and testable without a key-distribution
 * dance. The JWKS/RS256 verification plugs in exactly where {@link #verifySignature} lives.
 *
 * <p>The point the article makes: the launch is the security boundary. A tool that trusts an unsigned or
 * unverified launch is an open door into a course's roster and grades. Validation is not optional ceremony.
 */
@Component
public class LtiLaunchValidator {

    private final String signingSecret;
    private final Clock clock;

    public LtiLaunchValidator() {
        this("lti-platform-shared-secret", Clock.systemUTC());
    }

    /** Construct with an explicit secret and clock — used by the platform side and by tests to mint launches. */
    public LtiLaunchValidator(String signingSecret, Clock clock) {
        this.signingSecret = signingSecret;
        this.clock = clock;
    }

    /** Thrown when a launch token is missing, tampered, expired, or malformed — never trust such a launch. */
    public static class InvalidLaunchException extends RuntimeException {
        public InvalidLaunchException(String message) {
            super(message);
        }
    }

    /**
     * Mint a launch token (what the platform does). Format: base64(payload).hmac(payload). Payload is
     * {@code k=v} pairs including an {@code exp} epoch-seconds expiry.
     */
    public String mintLaunch(Map<String, String> claims, long expEpochSeconds) {
        Map<String, String> all = new HashMap<>(claims);
        all.put("exp", Long.toString(expEpochSeconds));
        String payload = encode(all);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + sign(b64);
    }

    /**
     * Verify a launch token and return its claims, or throw {@link InvalidLaunchException}. Order matters:
     * verify the signature <em>before</em> trusting anything in the payload, then check expiry.
     */
    public LtiClaims validate(String token) {
        if (token == null || !token.contains(".")) {
            throw new InvalidLaunchException("missing or malformed launch token");
        }
        String[] segs = token.split("\\.", 2);
        String b64 = segs[0];
        String sig = segs[1];
        if (!verifySignature(b64, sig)) {
            throw new InvalidLaunchException("bad signature — launch not trusted");
        }
        Map<String, String> claims = decode(new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8));

        long exp = Long.parseLong(claims.getOrDefault("exp", "0"));
        if (Instant.now(clock).getEpochSecond() > exp) {
            throw new InvalidLaunchException("launch token expired");
        }
        return new LtiClaims(
            claims.get("sub"),
            claims.get("context_id"),
            claims.get("resource_link_id"),
            claims.getOrDefault("role", "Learner"),
            claims.get("line_item_url"),
            Boolean.parseBoolean(claims.getOrDefault("deep_linking", "false"))
        );
    }

    private boolean verifySignature(String b64, String sig) {
        // constant-time compare in spirit; equals is fine for a teaching stand-in
        return sign(b64).equals(sig);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private String encode(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private Map<String, String> decode(String s) {
        Map<String, String> m = new HashMap<>();
        for (String line : s.split("\n")) {
            int i = line.indexOf('=');
            if (i > 0) {
                m.put(line.substring(0, i), line.substring(i + 1));
            }
        }
        return m;
    }
}
