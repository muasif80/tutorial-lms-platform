/**
 * Auth bounded context: authentication and role-based access control for the working UI. Implemented in
 * Part 11.
 *
 * <p>It turns the platform from an open backend into a multi-user product. A global
 * {@link com.scholr.lms.auth.domain.Credential} holds the login (email, BCrypt hash, role, tenant);
 * {@link com.scholr.lms.auth.AppUserDetailsService} loads it for Spring Security form login;
 * {@link com.scholr.lms.auth.SecurityConfig} enforces RBAC on the UI routes (admin / instructor /
 * student); and {@link com.scholr.lms.auth.TenantPrincipalFilter} pins the request's tenant from the
 * authenticated user, so isolation is driven by who is logged in rather than a trusted header.
 *
 * <p>Credentials are global (no tenant filter) because login precedes tenant context; everything the user
 * then touches is tenant-scoped as usual.
 */
package com.scholr.lms.auth;
