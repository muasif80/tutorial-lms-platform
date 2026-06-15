package com.scholr.lms.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.scholr.lms.catalog.domain.Lesson;
import com.scholr.lms.catalog.domain.MediaBlob;
import com.scholr.lms.catalog.domain.Section;
import com.scholr.lms.catalog.internal.CourseRepository;
import com.scholr.lms.catalog.internal.LessonRepository;
import com.scholr.lms.catalog.internal.MediaBlobRepository;
import com.scholr.lms.catalog.internal.SectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Part 15: rich lesson authoring. Lessons become ordered lists of {@link Section}s, each authored in a
 * block editor and stored as block JSON (the editable source) plus server-rendered, sanitized HTML (what
 * learners see). This service owns section/lesson CRUD and ordering, and the demo image-blob store. Every
 * save runs the content through {@link BlockRenderer} → {@code HtmlSanitizer}, so untrusted authored markup
 * is sanitized once, at the boundary, before it can ever reach a reader.
 */
@Service
public class AuthoringService {

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final SectionRepository sections;
    private final MediaBlobRepository blobs;
    private final BlockRenderer renderer;

    public AuthoringService(CourseRepository courses, LessonRepository lessons, SectionRepository sections,
                            MediaBlobRepository blobs, BlockRenderer renderer) {
        this.courses = courses;
        this.lessons = lessons;
        this.sections = sections;
        this.blobs = blobs;
        this.renderer = renderer;
    }

    // --- sections ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Section> sections(UUID lessonId) {
        return sections.findByLessonIdOrderByPositionAsc(lessonId);
    }

    @Transactional(readOnly = true)
    public Section section(UUID sectionId) {
        return sections.findById(sectionId).orElseThrow(() -> new IllegalArgumentException("no section " + sectionId));
    }

    @Transactional(readOnly = true)
    public Lesson lesson(UUID lessonId) {
        return lessons.findById(lessonId).orElseThrow(() -> new IllegalArgumentException("no lesson " + lessonId));
    }

    /** Append a new (empty) section to a lesson at the next position. */
    @Transactional
    public Section addSection(UUID lessonId, String title) {
        Lesson lesson = lessons.findById(lessonId)
            .orElseThrow(() -> new IllegalArgumentException("no lesson " + lessonId));
        int next = (int) sections.countByLessonId(lessonId) + 1;
        return sections.save(Section.of(lesson.courseId(), lessonId, title, next, Instant.now()));
    }

    /** Save a section's title and block content — rendered to sanitized HTML in the same step. */
    @Transactional
    public Section saveSection(UUID sectionId, String title, String contentJson) {
        Section s = section(sectionId);
        String safeHtml = renderer.render(contentJson); // render + sanitize at the trust boundary
        s.edit(title == null || title.isBlank() ? "Untitled section" : title.trim(),
            contentJson, safeHtml, Instant.now());
        return sections.save(s);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        sections.deleteById(sectionId);
    }

    /** Reorder a section within its lesson by swapping positions with its neighbour. */
    @Transactional
    public void moveSection(UUID sectionId, boolean up) {
        Section s = section(sectionId);
        List<Section> ordered = sections.findByLessonIdOrderByPositionAsc(s.lessonId());
        swapAndSave(ordered, s.id(), up);
    }

    // --- lessons (edit/reorder/delete; creation stays in CatalogService) -------------------------

    @Transactional
    public void renameLesson(UUID lessonId, String title) {
        Lesson l = lessons.findById(lessonId).orElseThrow(() -> new IllegalArgumentException("no lesson " + lessonId));
        l.rename(title == null || title.isBlank() ? "Untitled lesson" : title.trim());
        lessons.save(l);
    }

    @Transactional
    public void moveLesson(UUID lessonId, boolean up) {
        Lesson l = lessons.findById(lessonId).orElseThrow(() -> new IllegalArgumentException("no lesson " + lessonId));
        List<Lesson> ordered = lessons.findByCourseIdOrderByPositionAsc(l.courseId());
        // reuse the same swap by mapping to a tiny positional interface
        int idx = indexOf(ordered.stream().map(Lesson::id).toList(), lessonId);
        int swap = up ? idx - 1 : idx + 1;
        if (idx < 0 || swap < 0 || swap >= ordered.size()) {
            return;
        }
        Lesson a = ordered.get(idx), b = ordered.get(swap);
        int pa = a.position(), pb = b.position();
        a.moveTo(pb);
        b.moveTo(pa);
        lessons.save(a);
        lessons.save(b);
    }

    /** Delete a lesson and its sections. (Demo: assumes no learner completions reference it.) */
    @Transactional
    public void deleteLesson(UUID lessonId) {
        sections.findByLessonIdOrderByPositionAsc(lessonId).forEach(s -> sections.deleteById(s.id()));
        lessons.deleteById(lessonId);
    }

    // --- demo image blob store --------------------------------------------------------------------

    @Transactional
    public UUID storeBlob(String contentType, String filename, byte[] data) {
        String b64 = java.util.Base64.getEncoder().encodeToString(data);
        MediaBlob blob = blobs.save(MediaBlob.of(
            contentType == null ? "application/octet-stream" : contentType,
            filename == null ? "upload" : filename, b64, Instant.now()));
        return blob.id();
    }

    @Transactional(readOnly = true)
    public MediaBlob blob(UUID id) {
        return blobs.findById(id).orElseThrow(() -> new IllegalArgumentException("no blob " + id));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void swapAndSave(List<Section> ordered, UUID id, boolean up) {
        int idx = indexOf(ordered.stream().map(Section::id).toList(), id);
        int swap = up ? idx - 1 : idx + 1;
        if (idx < 0 || swap < 0 || swap >= ordered.size()) {
            return;
        }
        Section a = ordered.get(idx), b = ordered.get(swap);
        int pa = a.position(), pb = b.position();
        a.moveTo(pb);
        b.moveTo(pa);
        sections.save(a);
        sections.save(b);
    }

    private static int indexOf(List<UUID> ids, UUID id) {
        return ids.indexOf(id);
    }
}
