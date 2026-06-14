package com.scholr.lms.billing.internal;

import java.util.UUID;

import com.scholr.lms.billing.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface PlanRepository extends JpaRepository<Plan, UUID> {
}
