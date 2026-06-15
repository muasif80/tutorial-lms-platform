package com.scholr.lms.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.scholr.lms.shared.HtmlSanitizer;
import org.junit.jupiter.api.Test;

/**
 * Part 15: proves the stored-XSS trust boundary. Authored HTML is untrusted; this is the allow-list that
 * decides what survives. If these pass, a malicious lesson can't run script in a learner's browser.
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void strips_script_tags() {
        String out = sanitizer.sanitize("<p>Lesson</p><script>alert(document.cookie)</script>");
        assertThat(out).contains("Lesson").doesNotContainIgnoringCase("script").doesNotContain("alert");
    }

    @Test
    void strips_event_handlers_and_js_urls() {
        assertThat(sanitizer.sanitize("<p onclick=\"steal()\">x</p>")).doesNotContain("onclick");
        assertThat(sanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>")).doesNotContain("javascript:");
        assertThat(sanitizer.sanitize("<img src=\"javascript:alert(1)\">")).doesNotContain("javascript:");
    }

    @Test
    void keeps_safe_rich_content() {
        String in = "<h2>Topic</h2><p><b>bold</b> and <a href=\"https://x.io\">link</a></p>"
            + "<ul><li>one</li></ul><pre><code>code()</code></pre><table><tr><td>c</td></tr></table>";
        String out = sanitizer.sanitize(in);
        assertThat(out).contains("<h2>", "<b>bold</b>", "href=\"https://x.io\"", "<li>one</li>", "<pre>", "<table>");
    }

    @Test
    void allows_iframe_from_allowlisted_host_only() {
        assertThat(sanitizer.sanitize("<iframe src=\"https://www.youtube.com/embed/abc\"></iframe>"))
            .contains("youtube.com");
        assertThat(sanitizer.sanitize("<iframe src=\"https://evil.example.com/x\"></iframe>"))
            .doesNotContain("iframe").doesNotContain("evil.example.com");
    }

    @Test
    void keeps_app_relative_image_urls() {
        // Regression: an uploaded image is referenced by an app-relative URL (/media/blob/{id}).
        // This must survive sanitization — earlier tests only used absolute URLs and missed this.
        assertThat(sanitizer.sanitize("<figure><img src=\"/media/blob/abc-123\" alt=\"x\"></figure>"))
            .contains("src=\"/media/blob/abc-123\"");
        // ...while an absolute javascript: URL is still stripped (it carries a disallowed scheme).
        assertThat(sanitizer.sanitize("<img src=\"javascript:alert(1)\">")).doesNotContain("javascript:");
    }

    @Test
    void keeps_safe_media_embeds() {
        assertThat(sanitizer.sanitize("<video src=\"https://cdn.x/clip.mp4\" controls></video>")).contains("<video");
        assertThat(sanitizer.sanitize("<audio src=\"https://cdn.x/a.mp3\" controls></audio>")).contains("<audio");
    }
}
