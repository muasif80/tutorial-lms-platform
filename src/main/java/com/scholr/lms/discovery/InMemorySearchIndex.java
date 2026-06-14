package com.scholr.lms.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.scholr.lms.discovery.domain.CourseDocument;
import org.springframework.stereotype.Component;

/**
 * An in-process {@link SearchIndex} — the default so search runs and is testable without an OpenSearch
 * cluster. It models the two properties that matter: documents live in named <em>physical</em> indexes,
 * and an <em>alias</em> indirection lets you swap which physical index serves traffic atomically (the
 * zero-downtime reindex). Tenant filtering is done explicitly here, because — unlike the Postgres tables
 * of earlier parts — a search store has no Row-Level Security to lean on.
 *
 * <p>In production this bean is replaced by an OpenSearch-backed implementation; nothing above the
 * {@link SearchIndex} port changes.
 */
@Component
public class InMemorySearchIndex implements SearchIndex {

    /** physicalIndex → (courseId → document). */
    private final Map<String, Map<UUID, CourseDocument>> indexes = new ConcurrentHashMap<>();
    /** alias → physicalIndex. */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    @Override
    public void createIndex(String physicalIndex) {
        indexes.putIfAbsent(physicalIndex, new ConcurrentHashMap<>());
    }

    @Override
    public void index(String physicalIndex, CourseDocument doc) {
        indexes.computeIfAbsent(physicalIndex, k -> new ConcurrentHashMap<>()).put(doc.courseId(), doc);
    }

    @Override
    public void assignAlias(String alias, String physicalIndex) {
        // Atomic repoint: one map write flips every future search to the new index at once.
        aliases.put(alias, physicalIndex);
    }

    @Override
    public List<CourseDocument> searchByAlias(String alias, UUID tenantId) {
        String physical = aliases.get(alias);
        if (physical == null) {
            return List.of();
        }
        Map<UUID, CourseDocument> docs = indexes.getOrDefault(physical, Map.of());
        List<CourseDocument> tenantDocs = new ArrayList<>();
        for (CourseDocument doc : docs.values()) {
            if (doc.tenantId().equals(tenantId)) { // hand-rolled isolation — no RLS out here
                tenantDocs.add(doc);
            }
        }
        return tenantDocs;
    }

    /** The physical index an alias currently points at (test/observability helper). */
    public String resolveAlias(String alias) {
        return aliases.get(alias);
    }
}
