package com.scholr.lms.identity;

import java.util.UUID;

import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Membership;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.identity.domain.Role;
import com.scholr.lms.identity.internal.AppUserRepository;
import com.scholr.lms.identity.internal.MembershipRepository;
import com.scholr.lms.identity.internal.OrganizationRepository;
import org.springframework.stereotype.Service;

/** Public API of the Identity &amp; Org context. */
@Service
public class IdentityService {

    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final MembershipRepository memberships;

    public IdentityService(OrganizationRepository organizations, AppUserRepository users,
                           MembershipRepository memberships) {
        this.organizations = organizations;
        this.users = users;
        this.memberships = memberships;
    }

    /** Creates a tenant. The returned id is the tenant id used everywhere else. */
    public Organization createOrganization(String name) {
        return organizations.save(Organization.create(name));
    }

    public AppUser createUser(String email, String name) {
        return users.save(AppUser.create(email, name));
    }

    /** Grants the current tenant's user a role (tenant set automatically). */
    public Membership addMembership(UUID userId, Role role) {
        return memberships.save(Membership.create(userId, role));
    }
}
