package com.scholr.lms.media.internal;

import java.util.UUID;

import com.scholr.lms.media.domain.VideoAsset;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface VideoAssetRepository extends JpaRepository<VideoAsset, UUID> {
}
