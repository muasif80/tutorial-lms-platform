package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.discovery.IndexingService;
import com.scholr.lms.discovery.InMemorySearchIndex;
import com.scholr.lms.discovery.RecommendationService;
import com.scholr.lms.discovery.SearchMode;
import com.scholr.lms.discovery.SearchService;
import com.scholr.lms.discovery.domain.CourseDocument;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 6 guarantees: keyword vs semantic vs hybrid search rank differently and sensibly,
 * content-based recommendations and the cold-start fallback work, tenant isolation holds in a search
 * store with no Row-Level Security, and a full reindex swaps the alias with zero downtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DiscoverySearchTest {

    @Autowired private IndexingService indexing;
    @Autowired private InMemorySearchIndex index;
    @Autowired private SearchService search;
    @Autowired private RecommendationService recommendations;

    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private CourseDocument doc(UUID tenant, String title, List<String> keywords, long pop, double completion) {
        return indexing.toDocument(UUID.randomUUID(), tenant, title, keywords, pop, completion);
    }

    @Test
    void keyword_semantic_and_hybrid_rank_differently() {
        CourseDocument java = doc(acme, "Java Programming",
            List.of("java", "jvm", "object oriented", "backend"), 100, 0.7);
        CourseDocument kotlin = doc(acme, "Kotlin for Backend",
            List.of("kotlin", "jvm", "object oriented", "backend"), 50, 0.6);
        CourseDocument cooking = doc(acme, "Italian Cooking",
            List.of("pasta", "sauce", "kitchen"), 200, 0.9);
        indexing.reindexAll(List.of(java, kotlin, cooking));

        TenantContext.set(TenantId.of(acme));

        // KEYWORD: only documents containing the literal term "java" match.
        var kw = search.search("java", SearchMode.KEYWORD);
        assertTrue(kw.stream().anyMatch(h -> h.document().title().equals("Java Programming")));
        assertFalse(kw.stream().anyMatch(h -> h.document().title().equals("Kotlin for Backend")),
            "keyword search for 'java' must not return Kotlin (no shared literal term)");

        // SEMANTIC: a conceptually-related query surfaces the JVM/backend courses, never the cooking one.
        var sem = search.search("jvm backend object oriented", SearchMode.SEMANTIC);
        assertTrue(sem.stream().anyMatch(h -> h.document().title().equals("Java Programming")));
        assertTrue(sem.stream().anyMatch(h -> h.document().title().equals("Kotlin for Backend")),
            "semantic search relates Kotlin to a JVM/backend query");
        assertFalse(sem.stream().anyMatch(h -> h.document().title().equals("Italian Cooking")),
            "unrelated content stays out of semantic results");

        // HYBRID: returns results and ranks a relevant course above the unrelated one.
        var hyb = search.search("java backend", SearchMode.HYBRID);
        assertFalse(hyb.isEmpty());
        assertEquals("Java Programming", hyb.get(0).document().title(),
            "hybrid ranks the best lexical+semantic match first");
    }

    @Test
    void recommendations_are_content_based_with_a_cold_start_fallback() {
        CourseDocument java = doc(acme, "Java Programming", List.of("java", "jvm", "backend"), 100, 0.7);
        CourseDocument kotlin = doc(acme, "Kotlin for Backend", List.of("kotlin", "jvm", "backend"), 50, 0.6);
        CourseDocument cooking = doc(acme, "Italian Cooking", List.of("pasta", "kitchen"), 500, 0.9);
        indexing.reindexAll(List.of(java, kotlin, cooking));
        TenantContext.set(TenantId.of(acme));

        // Content-based: most-similar to Java is Kotlin (shared jvm/backend), not the popular cooking course.
        List<CourseDocument> similar = recommendations.similarTo(java.courseId(), 1);
        assertEquals("Kotlin for Backend", similar.get(0).title(),
            "content-based rec ignores raw popularity in favor of similarity");

        // Cold start (no seed, no history): fall back to the most popular/best-completed course.
        List<CourseDocument> cold = recommendations.popular(1);
        assertEquals("Italian Cooking", cold.get(0).title(), "cold-start falls back to popularity");
    }

    @Test
    void search_is_tenant_isolated_without_row_level_security() {
        CourseDocument acmeDoc = doc(acme, "Acme Secret Course", List.of("secret", "internal"), 10, 0.5);
        CourseDocument globexDoc = doc(globex, "Globex Secret Course", List.of("secret", "internal"), 10, 0.5);
        indexing.reindexAll(List.of(acmeDoc, globexDoc));

        TenantContext.set(TenantId.of(acme));
        var acmeHits = search.search("secret internal", SearchMode.HYBRID);
        assertEquals(1, acmeHits.size(), "Acme sees only its own course");
        assertEquals("Acme Secret Course", acmeHits.get(0).document().title());

        TenantContext.set(TenantId.of(globex));
        var globexHits = search.search("secret internal", SearchMode.HYBRID);
        assertEquals(1, globexHits.size(), "Globex sees only its own course");
        assertEquals("Globex Secret Course", globexHits.get(0).document().title());
    }

    @Test
    void full_reindex_swaps_the_alias_with_zero_downtime() {
        CourseDocument v1 = doc(acme, "Original Title", List.of("alpha"), 1, 0.1);
        indexing.reindexAll(List.of(v1));
        String firstPhysical = index.resolveAlias(SearchService.COURSES_ALIAS);
        TenantContext.set(TenantId.of(acme));
        assertFalse(search.search("alpha", SearchMode.KEYWORD).isEmpty(), "v1 is searchable");

        // Rebuild into a brand-new physical index; the alias only flips at the very end.
        CourseDocument v2 = doc(acme, "Updated Title", List.of("beta"), 1, 0.1);
        String secondPhysical = indexing.reindexAll(List.of(v2));

        assertFalse(firstPhysical.equals(secondPhysical), "reindex builds a new physical index, not in place");
        assertEquals(secondPhysical, index.resolveAlias(SearchService.COURSES_ALIAS), "alias now points at the new index");
        assertTrue(search.search("beta", SearchMode.KEYWORD).size() == 1, "new content is live after the swap");
        assertTrue(search.search("alpha", SearchMode.KEYWORD).isEmpty(), "old content is gone after the swap");
    }
}
