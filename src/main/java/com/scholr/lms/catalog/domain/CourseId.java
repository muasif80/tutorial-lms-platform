package com.scholr.lms.catalog.domain;

import java.util.UUID;

public record CourseId(UUID value) {

    public static CourseId random() {
        return new CourseId(UUID.randomUUID());
    }
}
