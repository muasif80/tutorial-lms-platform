package com.scholr.lms.web;

import java.util.UUID;

import com.scholr.lms.catalog.AuthoringService;
import com.scholr.lms.catalog.domain.MediaBlob;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Part 15: serves images uploaded from the lesson block editor (the demo blob store). Requires
 * authentication and is tenant-scoped — the blob is loaded through the tenant-filtered repository, so a
 * user can only ever fetch an image belonging to their own organisation. Large media (video/audio/PDF)
 * is never served here; it's referenced by URL, per Part 3.
 */
@Controller
public class MediaBlobController {

    private final AuthoringService authoring;

    public MediaBlobController(AuthoringService authoring) {
        this.authoring = authoring;
    }

    @GetMapping("/media/blob/{id}")
    public ResponseEntity<byte[]> blob(@PathVariable UUID id) {
        try {
            MediaBlob b = authoring.blob(id);
            byte[] bytes = java.util.Base64.getDecoder().decode(b.dataB64());
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(b.contentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
                .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
