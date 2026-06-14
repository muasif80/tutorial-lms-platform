package com.scholr.lms.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.scholr.lms.discovery.domain.CourseDocument;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.springframework.stereotype.Service;

/**
 * Course recommendations for the current tenant, with an explicit answer to the <strong>cold-start
 * problem</strong> — the hardest and most-skipped part of any recommender.
 *
 * <p>Two regimes:
 * <ul>
 *   <li><b>Content-based "more like this"</b> — given a course a learner liked, rank the rest by cosine
 *       similarity of their embeddings. This works from day one because it needs only item content, not
 *       interaction history; it is the standard escape from item cold-start.</li>
 *   <li><b>Cold-start fallback</b> — for a brand-new learner with no history and no seed course, there is
 *       nothing personal to compute, so we fall back to a sensible non-personalized ranking: the most
 *       popular, best-completed courses. A good default beats an empty shelf, and it gathers the very
 *       interaction signal that lets collaborative filtering take over later.</li>
 * </ul>
 *
 * <p>This is deliberately content-based rather than collaborative-filtering, because CF needs a dense
 * interaction matrix the platform won't have early — the article explains when to graduate to it.
 */
@Service
public class RecommendationService {

    private final SearchIndex index;
    private final TextVectorizer vectorizer;

    public RecommendationService(SearchIndex index, TextVectorizer vectorizer) {
        this.index = index;
        this.vectorizer = vectorizer;
    }

    /** "More courses like this one" — content-based, ranked by embedding similarity. Excludes the seed. */
    public List<CourseDocument> similarTo(UUID seedCourseId, int limit) {
        UUID tenantId = currentTenant();
        List<CourseDocument> all = index.searchByAlias(SearchService.COURSES_ALIAS, tenantId);
        CourseDocument seed = all.stream()
            .filter(d -> d.courseId().equals(seedCourseId))
            .findFirst()
            .orElse(null);
        if (seed == null) {
            return popular(limit); // unknown seed → degrade gracefully to the cold-start ranking
        }
        return all.stream()
            .filter(d -> !d.courseId().equals(seedCourseId))
            .sorted(Comparator.comparingDouble((CourseDocument d) -> vectorizer.cosine(seed.vector(), d.vector())).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Cold-start recommendations for a learner with no history: the most popular, best-completed courses.
     * Non-personalized by design — the honest answer when there is no signal to personalize on yet.
     */
    public List<CourseDocument> popular(int limit) {
        UUID tenantId = currentTenant();
        return index.searchByAlias(SearchService.COURSES_ALIAS, tenantId).stream()
            .sorted(Comparator
                .comparingLong(CourseDocument::popularity)
                .thenComparingDouble(CourseDocument::completionRate)
                .reversed())
            .limit(limit)
            .toList();
    }

    private UUID currentTenant() {
        TenantId t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("no tenant in context for recommendations");
        }
        return t.value();
    }
}
