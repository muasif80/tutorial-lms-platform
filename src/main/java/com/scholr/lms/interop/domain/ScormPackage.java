package com.scholr.lms.interop.domain;

/**
 * A parsed SCORM package — the metadata extracted from its {@code imsmanifest.xml}. A SCORM package is a zip
 * of HTML/JS content plus a manifest that declares the title and the launch file; the LMS reads the manifest
 * on import, stores the content, and later launches the {@code launchHref} inside a sandboxed iframe.
 *
 * @param title      human-readable course title from the manifest
 * @param launchHref the entry file the runtime launches (e.g. {@code index.html})
 * @param version    the SCORM version string (e.g. {@code 1.2} or {@code 2004})
 */
public record ScormPackage(String title, String launchHref, String version) {
}
