package com.scholr.lms.enrollment.domain;

import java.util.UUID;

public record CohortId(UUID value) {

    public static CohortId random() {
        return new CohortId(UUID.randomUUID());
    }
}
