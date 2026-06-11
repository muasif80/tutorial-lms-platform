package com.scholr.lms.enrollment.domain;

import java.util.UUID;

public record LearnerId(UUID value) {

    public static LearnerId random() {
        return new LearnerId(UUID.randomUUID());
    }
}
