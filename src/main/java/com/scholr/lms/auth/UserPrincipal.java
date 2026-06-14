package com.scholr.lms.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.scholr.lms.auth.domain.Credential;
import com.scholr.lms.identity.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal. Beyond Spring Security's username/password/authorities, it carries the
 * platform-specific identity the rest of the app needs: the domain {@code userId} and the {@code tenantId},
 * so a request filter can set the tenant context from the logged-in user rather than a trusted header.
 *
 * <p>The {@link Role} maps to a Spring authority: ORG_ADMIN → {@code ROLE_ADMIN}, INSTRUCTOR →
 * {@code ROLE_INSTRUCTOR}, LEARNER → {@code ROLE_STUDENT}. Those authorities are what gate the UI routes.
 */
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final String displayName;

    public UserPrincipal(Credential c) {
        this.userId = c.appUserId();
        this.tenantId = c.tenantId();
        this.email = c.email();
        this.passwordHash = c.passwordHash();
        this.role = c.role();
        this.displayName = c.displayName();
    }

    public static String authorityFor(Role role) {
        return switch (role) {
            case ORG_ADMIN -> "ROLE_ADMIN";
            case INSTRUCTOR -> "ROLE_INSTRUCTOR";
            case LEARNER -> "ROLE_STUDENT";
        };
    }

    public UUID userId() {
        return userId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Role role() {
        return role;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(authorityFor(role)));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
