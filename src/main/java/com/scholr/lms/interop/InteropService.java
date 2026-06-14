package com.scholr.lms.interop;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.interop.domain.LtiClaims;
import com.scholr.lms.interop.domain.ScormPackage;
import com.scholr.lms.interop.domain.XapiStatement;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.springframework.stereotype.Service;

/**
 * Public API of the Interop context — the three standards that make an LMS sellable to institutions, behind
 * one service. It does not own persistence; it bridges the platform to external systems through the ports in
 * this package (the LRS, the LTI platform) and the parsers/validators here.
 *
 * <ul>
 *   <li><b>xAPI</b>: {@link #publishToLrs} translates an internal event into an xAPI statement and records it
 *       — your event stream becomes your LRS feed.</li>
 *   <li><b>LTI 1.3</b>: {@link #handleLaunch} verifies a launch token (untrusted until proven), and
 *       {@link #passbackGrade} reports a score to the platform's gradebook via AGS.</li>
 *   <li><b>SCORM</b>: {@link #importPackage} safely parses a manifest, and {@link #commitScormCompletion}
 *       reliably captures the runtime's completion — the fix for the war-story bug where a SCORM course
 *       reported "complete" to its embedded API but the LMS never recorded it.</li>
 * </ul>
 */
@Service
public class InteropService {

    private final XapiTranslator translator;
    private final LearningRecordStore lrs;
    private final LtiLaunchValidator ltiValidator;
    private final LtiPlatform ltiPlatform;
    private final ScormManifestParser scormParser;

    public InteropService(XapiTranslator translator, LearningRecordStore lrs, LtiLaunchValidator ltiValidator,
                          LtiPlatform ltiPlatform, ScormManifestParser scormParser) {
        this.translator = translator;
        this.lrs = lrs;
        this.ltiValidator = ltiValidator;
        this.ltiPlatform = ltiPlatform;
        this.scormParser = scormParser;
    }

    /** Translate an internal event to an xAPI statement and record it in the LRS (no-op if no verb maps). */
    public Optional<XapiStatement> publishToLrs(OutboxEvent event) {
        Optional<XapiStatement> statement = translator.toStatement(event);
        statement.ifPresent(lrs::record);
        return statement;
    }

    /** Verify an LTI 1.3 launch and return its claims. Throws if the token isn't trustworthy. */
    public LtiClaims handleLaunch(String launchToken) {
        return ltiValidator.validate(launchToken);
    }

    /** Report a score back to the launching platform's gradebook (AGS grade passback). */
    public void passbackGrade(LtiClaims launch, int scorePercent) {
        if (launch.lineItemUrl() == null) {
            throw new IllegalStateException("launch carried no AGS line item — cannot pass back a grade");
        }
        ltiPlatform.sendGrade(launch.lineItemUrl(), launch.subject(), scorePercent);
    }

    /** Safely parse a SCORM manifest (XXE-hardened) into package metadata. */
    public ScormPackage importPackage(String manifestXml) {
        return scormParser.parse(manifestXml);
    }

    /**
     * Reliably capture a SCORM runtime completion — the war-story fix. A SCORM package reports its status to
     * its embedded JS API ({@code cmi.core.lesson_status}); the bug is that the commit never makes it back to
     * the LMS across the iframe/runtime boundary. The fix is a robust server-side commit endpoint that this
     * method models: it records the completion as a durable xAPI statement (idempotent on the statement's
     * derived identity, so a doubled commit from a flaky runtime can't double-record) and passes the score
     * along. Once the runtime's commit reaches here, the completion is captured and survives.
     *
     * @param lessonStatus the SCORM status string ({@code completed}, {@code passed}, {@code incomplete}, …)
     * @return the recorded statement if the status counts as completion, else empty
     */
    public Optional<XapiStatement> commitScormCompletion(UUID learnerId, UUID packageId,
                                                         String lessonStatus, Integer scorePercent) {
        boolean completed = "completed".equalsIgnoreCase(lessonStatus) || "passed".equalsIgnoreCase(lessonStatus);
        if (!completed) {
            return Optional.empty(); // not a completion; nothing durable to record yet
        }
        UUID tenantId = currentTenant();
        // Derive a stable statement id from (tenant, learner, package) so a duplicate commit dedupes in the LRS.
        UUID statementId = UUID.nameUUIDFromBytes((tenantId + ":" + learnerId + ":" + packageId).getBytes());
        XapiStatement statement = new XapiStatement(statementId, tenantId, learnerId, "completed", packageId, scorePercent);
        lrs.record(statement);
        return Optional.of(statement);
    }

    private UUID currentTenant() {
        TenantId t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("no tenant in context");
        }
        return t.value();
    }
}
