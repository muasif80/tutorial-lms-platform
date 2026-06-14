package com.scholr.lms.web;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.discovery.RecommendationService;
import com.scholr.lms.discovery.SearchMode;
import com.scholr.lms.discovery.SearchService;
import com.scholr.lms.discovery.domain.CourseDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the Discovery context: catalog search and recommendations, both scoped to the
 * request's tenant. The {@code mode} parameter exposes the keyword/semantic/hybrid choice to clients.
 */
@RestController
@RequestMapping("/api")
public class DiscoveryController {

    private final SearchService search;
    private final RecommendationService recommendations;

    public DiscoveryController(SearchService search, RecommendationService recommendations) {
        this.search = search;
        this.recommendations = recommendations;
    }

    public record SearchHitView(UUID courseId, String title, double score) {
    }

    public record CourseView(UUID courseId, String title, long popularity) {
    }

    @GetMapping("/search")
    public List<SearchHitView> search(@RequestParam String q,
                                      @RequestParam(defaultValue = "HYBRID") SearchMode mode) {
        return search.search(q, mode).stream()
            .map(h -> new SearchHitView(h.document().courseId(), h.document().title(), h.score()))
            .toList();
    }

    @GetMapping("/courses/{courseId}/similar")
    public List<CourseView> similar(@PathVariable UUID courseId,
                                    @RequestParam(defaultValue = "5") int limit) {
        return toViews(recommendations.similarTo(courseId, limit));
    }

    @GetMapping("/recommendations/popular")
    public List<CourseView> popular(@RequestParam(defaultValue = "5") int limit) {
        return toViews(recommendations.popular(limit));
    }

    private static List<CourseView> toViews(List<CourseDocument> docs) {
        return docs.stream().map(d -> new CourseView(d.courseId(), d.title(), d.popularity())).toList();
    }
}
