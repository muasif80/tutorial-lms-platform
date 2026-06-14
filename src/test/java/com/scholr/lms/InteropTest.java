package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.interop.InMemoryLtiPlatform;
import com.scholr.lms.interop.InteropService;
import com.scholr.lms.interop.LearningRecordStore;
import com.scholr.lms.interop.LtiLaunchValidator;
import com.scholr.lms.interop.ScormManifestParser;
import com.scholr.lms.interop.XapiTranslator;
import com.scholr.lms.interop.domain.LtiClaims;
import com.scholr.lms.interop.domain.ScormPackage;
import com.scholr.lms.interop.domain.XapiStatement;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 8 guarantees: internal events translate into xAPI statements recorded in a tenant-isolated
 * LRS, LTI 1.3 launches are validated (and tampered/expired ones rejected) with working grade passback, and
 * SCORM packages parse safely (XXE-hardened) with reliable, idempotent completion capture.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InteropTest {

    @Autowired private InteropService interop;
    @Autowired private LearningRecordStore lrs;
    @Autowired private InMemoryLtiPlatform ltiPlatform;
    @Autowired private XapiTranslator translator;
    @Autowired private ScormManifestParser scormParser;

    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void internal_events_become_xapi_statements_in_a_tenant_isolated_lrs() {
        UUID learner = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        // Translate an internal enrollment event into an xAPI "registered" statement.
        OutboxEvent event = OutboxEvent.of("enrollment.created", UUID.randomUUID(), learner + ":" + course, Instant.now());
        Optional<XapiStatement> stmt = translator.toStatement(event);
        assertTrue(stmt.isPresent());
        assertEquals("registered", stmt.get().verb());
        assertEquals(learner, stmt.get().actorId());
        assertEquals(course, stmt.get().objectId());

        // Record two statements under two tenants and confirm the LRS filters by tenant.
        lrs.record(XapiStatement.of(acme, learner, "completed", course, 90));
        lrs.record(XapiStatement.of(globex, learner, "completed", course, 10));
        assertEquals(1, lrs.statementsFor(acme, learner).size(), "Acme sees only its own statement");
        assertEquals(90, lrs.statementsFor(acme, learner).get(0).scorePercent());
        assertEquals(1, lrs.statementsFor(globex, learner).size(), "Globex sees only its own statement");
    }

    @Test
    void unmappable_events_do_not_fabricate_a_statement() {
        OutboxEvent unknown = OutboxEvent.of("video.transcoded", UUID.randomUUID(),
            UUID.randomUUID() + ":" + UUID.randomUUID(), Instant.now());
        assertTrue(translator.toStatement(unknown).isEmpty(), "no xAPI verb → no statement");
    }

    @Test
    void lti_launch_is_validated_and_a_grade_passes_back() {
        // The platform mints a signed launch with the same secret the validator trusts.
        LtiLaunchValidator platform = new LtiLaunchValidator("lti-platform-shared-secret",
            java.time.Clock.systemUTC());
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = platform.mintLaunch(Map.of(
            "sub", "user-123",
            "context_id", "course-abc",
            "resource_link_id", "rl-1",
            "role", "Learner",
            "line_item_url", "https://lms.test/ags/lineitems/1"), exp);

        LtiClaims claims = interop.handleLaunch(token);
        assertEquals("user-123", claims.subject());
        assertEquals("course-abc", claims.contextId());
        assertEquals("Learner", claims.role());

        interop.passbackGrade(claims, 88);
        List<InMemoryLtiPlatform.Passback> sent = ltiPlatform.sent();
        assertTrue(sent.stream().anyMatch(p -> p.subject().equals("user-123") && p.scorePercent() == 88),
            "the grade was passed back to the platform gradebook");
    }

    @Test
    void a_tampered_or_expired_lti_launch_is_rejected() {
        LtiLaunchValidator platform = new LtiLaunchValidator("lti-platform-shared-secret",
            java.time.Clock.systemUTC());
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = platform.mintLaunch(Map.of("sub", "user-9"), exp);

        // Tamper with the payload — the signature no longer matches.
        String tampered = "x" + token;
        assertThrows(LtiLaunchValidator.InvalidLaunchException.class, () -> interop.handleLaunch(tampered));

        // An expired token (exp in the past) is rejected even though its signature is valid.
        String expired = platform.mintLaunch(Map.of("sub", "user-9"), Instant.now().getEpochSecond() - 10);
        assertThrows(LtiLaunchValidator.InvalidLaunchException.class, () -> interop.handleLaunch(expired));
    }

    @Test
    void scorm_manifest_parses_safely_and_rejects_xxe() {
        String manifest = """
            <manifest>
              <metadata><schemaversion>1.2</schemaversion></metadata>
              <organizations><organization><title>Fire Safety Basics</title></organization></organizations>
              <resources><resource href="index.html"/></resources>
            </manifest>
            """;
        ScormPackage pkg = scormParser.parse(manifest);
        assertEquals("Fire Safety Basics", pkg.title());
        assertEquals("index.html", pkg.launchHref());
        assertEquals("1.2", pkg.version());

        // A malicious manifest declaring an external entity (XXE) must be refused, not parsed.
        String xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE manifest [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <manifest><resources><resource href="&xxe;"/></resources></manifest>
            """;
        assertThrows(IllegalArgumentException.class, () -> scormParser.parse(xxe),
            "a manifest with a DTD/external entity is rejected");
    }

    @Test
    void scorm_completion_capture_is_reliable_and_idempotent() {
        TenantContext.set(TenantId.of(acme));
        UUID learner = UUID.randomUUID();
        UUID pkg = UUID.randomUUID();

        // An "incomplete" status records nothing durable yet.
        assertTrue(interop.commitScormCompletion(learner, pkg, "incomplete", null).isEmpty());
        assertTrue(lrs.statementsFor(acme, learner).isEmpty());

        // A "completed" status is captured durably — the war-story fix.
        Optional<XapiStatement> first = interop.commitScormCompletion(learner, pkg, "completed", 95);
        assertTrue(first.isPresent());
        assertEquals(1, lrs.statementsFor(acme, learner).size());

        // A duplicate commit from a flaky runtime must NOT double-record (stable derived statement id).
        interop.commitScormCompletion(learner, pkg, "completed", 95);
        assertEquals(1, lrs.statementsFor(acme, learner).size(), "completion is captured exactly once");
        assertEquals(95, lrs.statementsFor(acme, learner).get(0).scorePercent());
    }
}
