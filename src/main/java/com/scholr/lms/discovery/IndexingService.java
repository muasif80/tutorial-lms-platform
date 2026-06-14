package com.scholr.lms.discovery;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.scholr.lms.discovery.domain.CourseDocument;
import org.springframework.stereotype.Service;

/**
 * Keeps the search index fresh and performs <strong>zero-downtime reindexing</strong>. In production its
 * inputs are Part 5's event stream (a {@code course.published} or {@code course.updated} event triggers a
 * single-document upsert) and periodic bulk backfills; here the inputs are passed in directly so the
 * mechanism is the focus.
 *
 * <p>Two operations, two scopes:
 * <ul>
 *   <li><b>Incremental upsert</b> — index one freshly-built document into the live physical index. This is
 *       the hot path the event stream drives, keeping the index seconds-fresh.</li>
 *   <li><b>Full reindex via alias swap</b> — build an entirely new physical index alongside the live one,
 *       fill it, and then atomically repoint the {@code courses} alias at it. Searches keep hitting the old
 *       index throughout the rebuild and flip to the new one in a single write — no downtime, no partial
 *       results. This is the fix for the stale/partial-index war story.</li>
 * </ul>
 */
@Service
public class IndexingService {

    private final SearchIndex index;
    private final TextVectorizer vectorizer;
    /** Monotonic suffix so each reindex builds a distinct physical index (courses_v1, courses_v2, …). */
    private final AtomicInteger version = new AtomicInteger(0);

    public IndexingService(SearchIndex index, TextVectorizer vectorizer) {
        this.index = index;
        this.vectorizer = vectorizer;
    }

    /** Build a search document from course fields, computing its embedding via the vectorizer seam. */
    public CourseDocument toDocument(UUID courseId, UUID tenantId, String title, List<String> keywords,
                                     long popularity, double completionRate) {
        String text = (title + " " + String.join(" ", keywords)).trim();
        return new CourseDocument(courseId, tenantId, title, keywords,
            vectorizer.vectorize(text), popularity, completionRate);
    }

    /** Incremental upsert into whichever physical index the alias currently serves. */
    public void upsert(String liveIndex, CourseDocument doc) {
        index.index(liveIndex, doc);
    }

    /**
     * Rebuild the catalog index from scratch and swap the alias to it atomically — zero downtime.
     *
     * @return the name of the new physical index now serving the {@code courses} alias
     */
    public String reindexAll(List<CourseDocument> documents) {
        String newIndex = "courses_v" + version.incrementAndGet();
        index.createIndex(newIndex);
        for (CourseDocument doc : documents) {
            index.index(newIndex, doc);   // fill the new index while the old one still serves traffic
        }
        index.assignAlias(SearchService.COURSES_ALIAS, newIndex); // atomic flip
        return newIndex;
    }
}
