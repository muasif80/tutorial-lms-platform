package com.scholr.lms.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.scholr.lms.discovery.domain.CourseDocument;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.springframework.stereotype.Service;

/**
 * Catalog search over the {@link SearchIndex}, in three modes (keyword, semantic, hybrid), always scoped
 * to the current tenant. This is where the article's central comparison becomes code: keyword search
 * requires the query's terms to be present and rewards their frequency (a BM25-flavored lexical score);
 * semantic search ranks by cosine similarity over embeddings and so surfaces conceptually-related courses
 * that share no exact words; hybrid blends the two and reranks, which is the pragmatic production default
 * because it catches both the precise match and the related-but-differently-worded one.
 *
 * <p>Tenant isolation is enforced explicitly via {@link TenantContext}: the index is a non-Postgres store
 * with no Row-Level Security, so the service reads the current tenant and only ever searches its documents.
 */
@Service
public class SearchService {

    /** A small, fixed weighting for hybrid; a real system tunes this and adds learned reranking. */
    private static final double LEXICAL_WEIGHT = 0.5;
    private static final double SEMANTIC_WEIGHT = 0.5;

    public static final String COURSES_ALIAS = "courses";

    private final SearchIndex index;
    private final TextVectorizer vectorizer;

    public SearchService(SearchIndex index, TextVectorizer vectorizer) {
        this.index = index;
        this.vectorizer = vectorizer;
    }

    public record Hit(CourseDocument document, double score) {
    }

    /** Search the current tenant's catalog. Results are ranked best-first. */
    public List<Hit> search(String query, SearchMode mode) {
        UUID tenantId = currentTenant();
        List<CourseDocument> candidates = index.searchByAlias(COURSES_ALIAS, tenantId);
        Set<String> queryTerms = terms(query);
        Map<Integer, Double> queryVector = vectorizer.vectorize(query);

        return candidates.stream()
            .map(doc -> new Hit(doc, score(doc, queryTerms, queryVector, mode)))
            .filter(hit -> hit.score() > 0.0)
            .sorted(Comparator.comparingDouble(Hit::score).reversed())
            .toList();
    }

    private double score(CourseDocument doc, Set<String> queryTerms, Map<Integer, Double> queryVector, SearchMode mode) {
        double lexical = lexicalScore(doc, queryTerms);
        double semantic = vectorizer.cosine(queryVector, doc.vector());
        double base = switch (mode) {
            case KEYWORD -> lexical;                                  // term must be present
            case SEMANTIC -> semantic;                                // cosine only
            case HYBRID -> LEXICAL_WEIGHT * lexical + SEMANTIC_WEIGHT * semantic;
        };
        if (base <= 0.0) {
            return 0.0;
        }
        // Rerank with ranking signals: popular, well-completed courses get a modest boost. Real systems
        // learn these weights; the point is that relevance is base-match * business signals, not match alone.
        double signalBoost = 1.0 + Math.log1p(doc.popularity()) * 0.05 + doc.completionRate() * 0.1;
        return base * signalBoost;
    }

    /** Fraction of query terms present in the document's searchable text — a simple lexical match score. */
    private double lexicalScore(CourseDocument doc, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> docTerms = terms(doc.searchableText());
        long present = queryTerms.stream().filter(docTerms::contains).count();
        return (double) present / queryTerms.size();
    }

    private Set<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(t -> t.length() >= 2)
            .collect(Collectors.toSet());
    }

    private UUID currentTenant() {
        TenantId t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("no tenant in context for search");
        }
        return t.value();
    }
}
