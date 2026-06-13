package com.scholr.lms.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A person on the platform. Identity is GLOBAL — the same human can be a learner in
 * one organization and an instructor in another — so AppUser is not tenant-scoped.
 * What is per-organization is the {@link Membership}.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    protected AppUser() {
    }

    public AppUser(UUID id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
    }

    public static AppUser create(String email, String name) {
        return new AppUser(UUID.randomUUID(), email, name);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }
}
