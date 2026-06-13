package com.scholr.lms.identity.internal;

import java.util.UUID;

import com.scholr.lms.identity.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
