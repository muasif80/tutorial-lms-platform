package com.scholr.lms.discovery.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A denormalized search document for one course — what gets indexed, not what's stored in Postgres.
 * Deliberately a plain immutable value, not a JPA entity: the search index lives in OpenSearch
 * (modeled here by an in-memory index), a different datastore from the system of record.
 *
 * <p>That separation has a sharp consequence the article dwells on: <strong>tenant isolation does not
 * come for free outside Postgres.</strong> There is no {@code @TenantId} and no Row-Level Security in
 * the search store, so the document carries its own {@code tenantId} and every query must filter on it
 * by hand. Leaving Postgres means re-earning the isolation guarantee Part 2 gave us automatically.
 *
 * <p>The document holds both the human-readable fields (for keyword search and display) and the
 * ranking signals (popularity, completion rate) that tune relevance. Its embedding vector is computed
 * by the {@code TextVectorizer} seam and kept on the document so semantic search is a cosine compare.
 */
public record CourseDocument(
    UUID courseId,
    UUID tenantId,
    String title,
    List<String> keywords,
    Map<Integer, Double> vector,
    long popularity,
    double completionRate
) {

    /** All searchable text — title plus keywords — used for keyword matching and (re)vectorization. */
    public String searchableText() {
        return (title + " " + String.join(" ", keywords)).trim();
    }

    /** A copy with an updated popularity signal (documents are immutable; indexing replaces them). */
    public CourseDocument withPopularity(long newPopularity) {
        return new CourseDocument(courseId, tenantId, title, keywords, vector, newPopularity, completionRate);
    }
}
