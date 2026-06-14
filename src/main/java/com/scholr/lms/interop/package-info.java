/**
 * Interoperability bounded context: the standards that make an LMS sellable to institutions — LTI 1.3,
 * SCORM, and xAPI. Implemented in Part 8.
 *
 * <p>An infra-bridging context (no JPA of its own), it connects the platform to external systems through
 * ports: {@link com.scholr.lms.interop.LearningRecordStore} (xAPI statements to an LRS — fed by translating
 * the Part 5 event stream via {@link com.scholr.lms.interop.XapiTranslator}),
 * {@link com.scholr.lms.interop.LtiPlatform} (AGS grade passback), with
 * {@link com.scholr.lms.interop.LtiLaunchValidator} verifying LTI 1.3 launches and
 * {@link com.scholr.lms.interop.ScormManifestParser} safely (XXE-hardened) reading untrusted SCORM packages.
 * {@link com.scholr.lms.interop.InteropService} ties them together, including the reliable, idempotent SCORM
 * completion capture that fixes the "course reported complete but the LMS never recorded it" bug.
 *
 * <p>Stores outside Postgres (the LRS) carry the tenant id and filter by hand, since there is no Row-Level
 * Security out there — the same discipline as the search index in Part 6.
 */
package com.scholr.lms.interop;
