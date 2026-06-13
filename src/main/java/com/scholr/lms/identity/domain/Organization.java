package com.scholr.lms.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Identity &amp; Org context: an organization is a tenant. Its id IS the tenant id.
 * This is the tenant registry, so it is deliberately NOT tenant-scoped (an operator
 * lists organizations across tenants).
 */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected Organization() {
    }

    public Organization(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public static Organization create(String name) {
        return new Organization(UUID.randomUUID(), name);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }
}
