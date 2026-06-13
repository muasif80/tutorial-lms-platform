package com.scholr.lms.identity.internal;

import java.util.UUID;

import com.scholr.lms.identity.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
