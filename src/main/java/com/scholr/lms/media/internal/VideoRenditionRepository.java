package com.scholr.lms.media.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.media.domain.VideoRendition;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface VideoRenditionRepository extends JpaRepository<VideoRendition, UUID> {

    /** All ABR rungs for an asset, used to build the master playlist / signed URLs. */
    List<VideoRendition> findByAssetIdOrderByHeightAsc(UUID assetId);
}
