package com.scholr.lms.interop.domain;

/**
 * The claims carried in an LTI 1.3 launch — extracted from the verified launch token. LTI 1.3 is an
 * OIDC-based handshake: a platform (the institution's LMS) launches a tool (you) by POSTing a signed JWT
 * whose claims identify the user, the course context, the specific resource link, the user's roles, and
 * the service endpoints the tool may call back into (notably the AGS line-item URL for grade passback).
 *
 * @param subject       the platform's stable user id (NOT a name — privacy by design)
 * @param contextId     the course/context the launch is in
 * @param resourceLinkId the specific placement being launched
 * @param role          the LTI role (e.g. Instructor, Learner)
 * @param lineItemUrl   the AGS endpoint to POST a score back to (grade passback); may be null
 * @param deepLinking   true if this is a deep-linking request (the tool returns content to embed)
 */
public record LtiClaims(
    String subject,
    String contextId,
    String resourceLinkId,
    String role,
    String lineItemUrl,
    boolean deepLinking
) {
}
