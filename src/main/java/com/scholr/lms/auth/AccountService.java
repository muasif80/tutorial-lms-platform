package com.scholr.lms.auth;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.auth.domain.Credential;
import com.scholr.lms.auth.internal.CredentialRepository;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Role;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration for the current tenant — the service behind the admin console's "people" screen.
 * Creating an account is a single, consistent operation across three pieces of the model: the domain user
 * ({@code app_users}), their per-tenant role ({@code memberships}), and their login ({@code credentials}).
 * Listing and counting are tenant-scoped by the authenticated principal's tenant.
 */
@Service
public class AccountService {

    private final IdentityService identity;
    private final CredentialRepository credentials;
    private final PasswordEncoder encoder;

    public AccountService(IdentityService identity, CredentialRepository credentials, PasswordEncoder encoder) {
        this.identity = identity;
        this.credentials = credentials;
        this.encoder = encoder;
    }

    public record AccountView(UUID userId, String email, String name, Role role) {
        public String roleLabel() {
            return switch (role) {
                case ORG_ADMIN -> "Admin";
                case INSTRUCTOR -> "Instructor";
                case LEARNER -> "Student";
            };
        }
    }

    public boolean emailTaken(String email) {
        return credentials.existsByEmail(email);
    }

    /**
     * Create a user, grant them a role in the current tenant, and issue their login — the admin's
     * "add instructor / add student" action. The tenant comes from the authenticated principal.
     */
    @Transactional
    public AccountView createAccount(String email, String name, Role role, String rawPassword) {
        UUID tenantId = currentTenant();
        AppUser user = identity.createUser(email, name);
        identity.addMembership(user.id(), role);
        credentials.save(Credential.of(email, encoder.encode(rawPassword), role, tenantId, user.id(), name));
        return new AccountView(user.id(), email, name, role);
    }

    @Transactional(readOnly = true)
    public List<AccountView> listForCurrentTenant() {
        return credentials.findByTenantIdOrderByDisplayName(currentTenant()).stream()
            .map(c -> new AccountView(c.appUserId(), c.email(), c.displayName(), c.role()))
            .toList();
    }

    @Transactional(readOnly = true)
    public long count(Role role) {
        return credentials.countByTenantIdAndRole(currentTenant(), role);
    }

    private UUID currentTenant() {
        TenantId t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("no authenticated tenant");
        }
        return t.value();
    }
}
