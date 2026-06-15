package com.scholr.lms.shared;

import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Part 15: the trust boundary for authored lesson content. Instructors author rich HTML in a block editor;
 * that HTML is rendered to other users (learners), so it is <strong>untrusted input</strong> and a classic
 * stored-XSS vector. This sanitizer is the single place that input crosses into trusted, renderable output.
 *
 * <p>It uses a jsoup allow-list (not a block-list): only known-safe tags and attributes survive, everything
 * else — {@code <script>}, {@code on*} handlers, {@code javascript:} URLs, style with expressions — is
 * stripped. Media embeds ({@code <iframe>}) are allowed only from an explicit host allow-list, so a section
 * can embed a YouTube video or a GitHub gist but not an arbitrary attacker-controlled frame.
 */
@Component
public class HtmlSanitizer {

    /** Hosts permitted in an {@code <iframe src>} (video, audio, docs, code embeds). */
    private static final Set<String> IFRAME_HOSTS = Set.of(
        "www.youtube.com", "youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com",
        "player.vimeo.com", "vimeo.com",
        "w.soundcloud.com",
        "gist.github.com",
        "docs.google.com", "drive.google.com",
        "codepen.io", "codesandbox.io"
    );

    private final Safelist safelist = buildSafelist();

    private static Safelist buildSafelist() {
        return Safelist.relaxed()
            // structural + semantic blocks beyond the relaxed defaults
            .addTags("figure", "figcaption", "hr", "mark", "section", "audio", "video", "source", "iframe", "cite")
            .addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("iframe", "src", "width", "height", "allow", "allowfullscreen", "frameborder", "loading")
            .addAttributes("video", "src", "controls", "width", "height", "poster")
            .addAttributes("audio", "src", "controls")
            .addAttributes("source", "src", "type")
            .addAttributes("pre", "class")
            .addAttributes("code", "class")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("div", "class")
            .addAttributes("span", "class")
            // only safe URL protocols are allowed on these attributes
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("iframe", "src", "https")
            .addProtocols("video", "src", "http", "https")
            .addProtocols("audio", "src", "http", "https")
            .addProtocols("source", "src", "http", "https")
            // keep app-relative URLs like an uploaded image's /media/blob/{id}; absolute URLs with a
            // disallowed scheme (e.g. javascript:) are still stripped because they carry a scheme.
            .preserveRelativeLinks(true);
    }

    /**
     * Clean untrusted HTML to a safe subset. Returns an empty string for null/blank input. After the
     * allow-list pass, a second pass drops any {@code <iframe>} whose host is not explicitly permitted.
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // Clean the parsed document directly (not via Jsoup.clean's string round-trip, which pretty-prints
        // and would inject newlines as text nodes). Compact output keeps the markup tight and preserves
        // whitespace inside <pre> code blocks.
        Document dirty = Jsoup.parseBodyFragment(html);
        Document doc = new Cleaner(safelist).clean(dirty);
        doc.outputSettings().prettyPrint(false);
        for (Element iframe : doc.select("iframe")) {
            if (!hostAllowed(iframe.attr("src"))) {
                iframe.remove();
            }
        }
        return doc.body().html();
    }

    private boolean hostAllowed(String src) {
        if (src == null || src.isBlank()) {
            return false;
        }
        try {
            String host = java.net.URI.create(src).getHost();
            return host != null && IFRAME_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
