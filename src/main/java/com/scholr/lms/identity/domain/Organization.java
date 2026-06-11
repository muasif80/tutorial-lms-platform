package com.scholr.lms.identity.domain;

import com.scholr.lms.shared.TenantId;

/** Identity &amp; Org context: an organization (tenant) on the platform. */
public record Organization(TenantId id, String name) {
}
