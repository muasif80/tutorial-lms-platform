package com.scholr.lms.discovery;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.discovery.domain.CourseDocument;

/**
 * The search-index seam — the boundary OpenSearch/Elasticsearch plugs into. {@link InMemorySearchIndex}
 * implements it for tests and dev; production swaps in an OpenSearch-backed implementation with no change
 * above this port.
 *
 * <p>The interface is deliberately shaped around the one operational feature that makes a search index
 * survivable in production: <strong>alias-based zero-downtime reindexing</strong>. Searches always go
 * through an <em>alias</em> (a stable name like {@code courses}) that points at a concrete physical index
 * ({@code courses_v7}). To reindex — a mapping change, an analyzer change, a full backfill — you build a
 * brand-new physical index alongside the live one and then atomically repoint the alias. Readers never
 * see a half-built index and never go dark; the swap is instant. Reindexing in place, by contrast, means
 * either downtime or serving partial results, which is how the war story in this part starts.
 */
public interface SearchIndex {

    /** Create a fresh, empty physical index by name (e.g. {@code courses_v8}). */
    void createIndex(String physicalIndex);

    /** Index (or replace) a document into a specific physical index. */
    void index(String physicalIndex, CourseDocument doc);

    /** Point an alias at a physical index — atomic. The previous target, if any, is simply dropped. */
    void assignAlias(String alias, String physicalIndex);

    /**
     * Search through an alias, returning candidate documents for the current tenant only. Ranking is the
     * caller's job ({@link SearchService}); this returns the tenant-filtered candidate set the ranker scores.
     */
    List<CourseDocument> searchByAlias(String alias, UUID tenantId);
}
