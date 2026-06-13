package com.scholr.lms.identity.internal;

import java.util.UUID;

import com.scholr.lms.identity.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
}
