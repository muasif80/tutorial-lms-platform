package com.scholr.lms.web;

import java.util.UUID;

import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IdentityController {

    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    public record CreateOrg(String name) {
    }

    public record OrgView(UUID id, String name) {
    }

    @PostMapping("/organizations")
    public OrgView createOrganization(@RequestBody CreateOrg request) {
        Organization org = identity.createOrganization(request.name());
        return new OrgView(org.id(), org.name());
    }

    public record CreateUser(String email, String name) {
    }

    public record UserView(UUID id, String email, String name) {
    }

    @PostMapping("/users")
    public UserView createUser(@RequestBody CreateUser request) {
        AppUser user = identity.createUser(request.email(), request.name());
        return new UserView(user.id(), user.email(), user.name());
    }
}
