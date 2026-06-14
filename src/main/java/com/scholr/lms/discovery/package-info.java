/**
 * Discovery bounded context: catalog search, recommendations, and the indexing pipeline that keeps them
 * fresh. Implemented in Part 6.
 *
 * <p>Unlike the persistence-heavy contexts, discovery lives in a search store (OpenSearch in production),
 * modeled here behind the {@link com.scholr.lms.discovery.SearchIndex} seam with an in-memory default —
 * the same port pattern as {@code realtime} and {@code events}. {@link com.scholr.lms.discovery.SearchService}
 * offers keyword, semantic, and hybrid search; {@link com.scholr.lms.discovery.RecommendationService}
 * handles content-based "more like this" and the cold-start fallback;
 * {@link com.scholr.lms.discovery.IndexingService} performs incremental upserts and zero-downtime
 * reindexing via an alias swap. Embeddings come from the {@link com.scholr.lms.discovery.TextVectorizer}
 * seam (a real model plugs in there).
 *
 * <p>Because the search store has no Postgres Row-Level Security, tenant isolation is enforced explicitly:
 * every document carries its {@code tenantId} and every query filters on the current tenant. Leaving the
 * database means re-earning, by hand, the isolation the earlier parts got for free.
 */
package com.scholr.lms.discovery;
