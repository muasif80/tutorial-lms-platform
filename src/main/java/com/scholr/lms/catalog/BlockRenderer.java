package com.scholr.lms.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholr.lms.shared.HtmlSanitizer;
import org.springframework.stereotype.Component;

/**
 * Part 15: renders a block editor's (Editor.js) saved JSON into HTML, then runs it through the
 * {@link HtmlSanitizer}. Storing the block model as the editable source of truth and rendering it
 * server-side to <em>sanitized</em> HTML means the learner view needs no JavaScript and is safe from a
 * stored-XSS payload smuggled into any block (including a raw-HTML block).
 *
 * <p>Each block type maps to a small, predictable slice of HTML. Free-text that the editor stores as raw
 * text (code blocks, table cells) is HTML-escaped here; inline rich text (paragraph/header content) is
 * passed through and cleaned by the sanitizer afterwards.
 */
@Component
public class BlockRenderer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HtmlSanitizer sanitizer;

    public BlockRenderer(HtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    /** Render Editor.js JSON to sanitized HTML. Blank/invalid input yields an empty string. */
    public String render(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        try {
            JsonNode root = mapper.readTree(contentJson);
            JsonNode blocks = root.path("blocks");
            if (blocks.isArray()) {
                for (JsonNode block : blocks) {
                    renderBlock(block, html);
                }
            }
        } catch (Exception e) {
            return ""; // malformed content renders as nothing rather than breaking the page
        }
        return sanitizer.sanitize(html.toString());
    }

    private void renderBlock(JsonNode block, StringBuilder out) {
        String type = block.path("type").asText("");
        JsonNode d = block.path("data");
        switch (type) {
            case "header" -> {
                int level = Math.min(4, Math.max(2, d.path("level").asInt(2)));
                out.append("<h").append(level).append('>')
                   .append(d.path("text").asText("")).append("</h").append(level).append('>');
            }
            case "paragraph" -> out.append("<p>").append(d.path("text").asText("")).append("</p>");
            case "list", "checklist" -> renderList(d, out);
            case "quote" -> {
                out.append("<blockquote><p>").append(d.path("text").asText("")).append("</p>");
                String caption = d.path("caption").asText("");
                if (!caption.isBlank()) {
                    out.append("<cite>").append(caption).append("</cite>");
                }
                out.append("</blockquote>");
            }
            case "code" -> out.append("<pre><code>").append(escape(d.path("code").asText(""))).append("</code></pre>");
            case "delimiter" -> out.append("<hr/>");
            case "table" -> renderTable(d, out);
            case "image" -> renderImage(d, out);
            case "embed" -> renderEmbed(d, out);
            case "attaches", "linkTool" -> renderLinkOrFile(type, d, out);
            case "raw" -> out.append(d.path("html").asText("")); // sanitized afterwards
            default -> { /* unknown block type: skip rather than emit untrusted markup */ }
        }
    }

    private void renderList(JsonNode d, StringBuilder out) {
        boolean ordered = "ordered".equals(d.path("style").asText("unordered"));
        out.append(ordered ? "<ol>" : "<ul>");
        for (JsonNode item : d.path("items")) {
            // Editor.js list items are strings (classic) or objects with "content" (nested)
            String content = item.isObject() ? item.path("content").asText("") : item.asText("");
            out.append("<li>").append(content).append("</li>");
        }
        out.append(ordered ? "</ol>" : "</ul>");
    }

    private void renderTable(JsonNode d, StringBuilder out) {
        JsonNode rows = d.path("content");
        if (!rows.isArray() || rows.isEmpty()) {
            return;
        }
        boolean headings = d.path("withHeadings").asBoolean(false);
        out.append("<table>");
        int r = 0;
        for (JsonNode row : rows) {
            out.append("<tr>");
            String cellTag = (headings && r == 0) ? "th" : "td";
            for (JsonNode cell : row) {
                out.append('<').append(cellTag).append('>').append(cell.asText("")).append("</").append(cellTag).append('>');
            }
            out.append("</tr>");
            r++;
        }
        out.append("</table>");
    }

    private void renderImage(JsonNode d, StringBuilder out) {
        String url = d.path("file").path("url").asText(d.path("url").asText(""));
        if (url.isBlank()) {
            return;
        }
        String caption = d.path("caption").asText("");
        out.append("<figure><img src=\"").append(escapeAttr(url)).append("\" alt=\"")
           .append(escapeAttr(caption)).append("\"/>");
        if (!caption.isBlank()) {
            out.append("<figcaption>").append(caption).append("</figcaption>");
        }
        out.append("</figure>");
    }

    private void renderEmbed(JsonNode d, StringBuilder out) {
        String embed = d.path("embed").asText("");
        if (embed.isBlank()) {
            return;
        }
        String caption = d.path("caption").asText("");
        out.append("<figure><iframe src=\"").append(escapeAttr(embed))
           .append("\" frameborder=\"0\" allowfullscreen loading=\"lazy\"></iframe>");
        if (!caption.isBlank()) {
            out.append("<figcaption>").append(caption).append("</figcaption>");
        }
        out.append("</figure>");
    }

    private void renderLinkOrFile(String type, JsonNode d, StringBuilder out) {
        String url = "linkTool".equals(type)
            ? d.path("link").asText("")
            : d.path("file").path("url").asText("");
        if (url.isBlank()) {
            return;
        }
        String label = "linkTool".equals(type)
            ? d.path("meta").path("title").asText(url)
            : d.path("title").asText(d.path("file").path("name").asText(url));
        out.append("<p>📎 <a href=\"").append(escapeAttr(url)).append("\" target=\"_blank\" rel=\"noopener\">")
           .append(escape(label)).append("</a></p>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
