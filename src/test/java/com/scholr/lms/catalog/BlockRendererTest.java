package com.scholr.lms.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.scholr.lms.shared.HtmlSanitizer;
import org.junit.jupiter.api.Test;

/** Part 15: the Editor.js block JSON renders to the expected (sanitized) HTML for the learner view. */
class BlockRendererTest {

    private final BlockRenderer renderer = new BlockRenderer(new HtmlSanitizer());

    @Test
    void renders_common_blocks() {
        String json = """
            {"blocks":[
              {"type":"header","data":{"text":"Topics","level":2}},
              {"type":"paragraph","data":{"text":"A <b>partitioned</b> log."}},
              {"type":"list","data":{"style":"unordered","items":["one","two"]}},
              {"type":"quote","data":{"text":"Quoted","caption":"Source"}},
              {"type":"delimiter","data":{}}
            ]}""";
        String html = renderer.render(json);
        assertThat(html).contains("<h2>Topics</h2>", "<b>partitioned</b>", "<li>one</li>", "<blockquote>", "<hr>");
    }

    @Test
    void code_block_is_escaped_not_executed() {
        String json = "{\"blocks\":[{\"type\":\"code\",\"data\":{\"code\":\"<script>alert(1)</script>\"}}]}";
        String html = renderer.render(json);
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void raw_html_block_is_sanitized() {
        String json = "{\"blocks\":[{\"type\":\"raw\",\"data\":{\"html\":\"<b>keep</b><script>evil()</script>\"}}]}";
        String html = renderer.render(json);
        assertThat(html).contains("<b>keep</b>").doesNotContainIgnoringCase("script");
    }

    @Test
    void youtube_embed_survives_but_arbitrary_iframe_does_not() {
        String yt = "{\"blocks\":[{\"type\":\"embed\",\"data\":{\"embed\":\"https://www.youtube.com/embed/x\"}}]}";
        assertThat(renderer.render(yt)).contains("youtube.com");
        String evil = "{\"blocks\":[{\"type\":\"raw\",\"data\":{\"html\":\"<iframe src='https://evil.test'></iframe>\"}}]}";
        assertThat(renderer.render(evil)).doesNotContain("evil.test");
    }

    @Test
    void table_and_image_render() {
        String table = "{\"blocks\":[{\"type\":\"table\",\"data\":{\"withHeadings\":true,\"content\":[[\"H\"],[\"v\"]]}}]}";
        assertThat(renderer.render(table)).contains("<table>", "<th>H</th>", "<td>v</td>");
        String img = "{\"blocks\":[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"https://cdn.x/p.png\"},\"caption\":\"Cap\"}}]}";
        assertThat(renderer.render(img)).contains("<figure>", "src=\"https://cdn.x/p.png\"", "<figcaption>Cap</figcaption>");
    }

    @Test
    void malformed_json_renders_empty() {
        assertThat(renderer.render("not json")).isEmpty();
        assertThat(renderer.render("")).isEmpty();
        assertThat(renderer.render(null)).isEmpty();
    }
}
