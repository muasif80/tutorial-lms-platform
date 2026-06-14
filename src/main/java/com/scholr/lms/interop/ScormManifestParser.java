package com.scholr.lms.interop;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.scholr.lms.interop.domain.ScormPackage;

/**
 * Parses a SCORM {@code imsmanifest.xml} into a {@link ScormPackage}. Two things make this more than a
 * formality. First, it extracts the launch file from the first {@code <resource>}'s {@code href} — the entry
 * point the runtime loads. Second, and more importantly, it parses untrusted third-party XML
 * <strong>securely</strong>: SCORM packages come from outside your trust boundary, and a naive XML parser is
 * vulnerable to XXE (XML External Entity) attacks that can read server files or trigger SSRF. So the parser
 * disables DTDs and external entities outright. Safely handling content you did not author is the whole
 * theme of embedding third-party packages, and it starts at the manifest.
 */
@Component
public class ScormManifestParser {

    public ScormPackage parse(String manifestXml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // XXE hardening — refuse DTDs and any external entity resolution on untrusted input
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(manifestXml.getBytes(StandardCharsets.UTF_8)));

            String version = detectVersion(doc);
            String title = firstText(doc, "title", "Untitled SCORM Package");
            String launchHref = firstResourceHref(doc);
            return new ScormPackage(title, launchHref, version);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid or unsafe SCORM manifest: " + e.getMessage(), e);
        }
    }

    private String detectVersion(Document doc) {
        NodeList schemaVersion = doc.getElementsByTagName("schemaversion");
        if (schemaVersion.getLength() > 0) {
            String v = schemaVersion.item(0).getTextContent().trim();
            return v.contains("2004") ? "2004" : v.startsWith("1.2") ? "1.2" : v;
        }
        return "unknown";
    }

    private String firstText(Document doc, String tag, String fallback) {
        NodeList nodes = doc.getElementsByTagName(tag);
        if (nodes.getLength() > 0 && !nodes.item(0).getTextContent().isBlank()) {
            return nodes.item(0).getTextContent().trim();
        }
        return fallback;
    }

    private String firstResourceHref(Document doc) {
        NodeList resources = doc.getElementsByTagName("resource");
        for (int i = 0; i < resources.getLength(); i++) {
            Node node = resources.item(i);
            if (node instanceof Element el && el.hasAttribute("href")) {
                return el.getAttribute("href");
            }
        }
        throw new IllegalArgumentException("no launchable resource (href) found in manifest");
    }
}
