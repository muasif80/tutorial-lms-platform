package com.scholr.lms.catalog.domain;

import com.scholr.lms.shared.TenantId;

/** Catalog context: the versioned definition of learning content. */
public record Course(CourseId id, TenantId tenant, String title, boolean published) {
}
