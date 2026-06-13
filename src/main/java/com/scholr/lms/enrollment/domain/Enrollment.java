package com.scholr.lms.enrollment.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/**
 * A learner's enrollment into a cohort. The unique constraint on
 * (tenant_id, cohort_id, learner_id) is what makes the "enroll" operation
 * idempotent at the database level — a retried request can't create a duplicate.
 */
@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "cohort_id", "learner_id"})
)
public class Enrollment {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    protected Enrollment() {
    }

    public Enrollment(UUID id, UUID cohortId, UUID learnerId) {
        this.id = id;
        this.cohortId = cohortId;
        this.learnerId = learnerId;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID cohortId() {
        return cohortId;
    }

    public UUID learnerId() {
        return learnerId;
    }
}
