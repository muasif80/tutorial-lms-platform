package com.scholr.lms.config;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.auth.domain.Credential;
import com.scholr.lms.auth.internal.CredentialRepository;
import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.identity.domain.Role;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a usable demo tenant on first startup so you can log in and use the platform immediately — an
 * organization, an admin, two instructors, four students (each with a login), a handful of courses, two
 * cohorts, and enrollments. Idempotent: it only seeds when the credentials table is empty, so restarts
 * (with a persistent volume) don't duplicate data.
 *
 * <p>Guarded by {@code scholr.seed=true} (set in the deployed config, absent in tests) so it never runs
 * during the test suite. Demo logins all use the password {@code scholr}.
 */
@Component
@ConditionalOnProperty(name = "scholr.seed", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "scholr";

    private final IdentityService identity;
    private final CatalogService catalog;
    private final EnrollmentService enrollment;
    private final CredentialRepository credentials;
    private final PasswordEncoder encoder;

    public DataSeeder(IdentityService identity, CatalogService catalog, EnrollmentService enrollment,
                      CredentialRepository credentials, PasswordEncoder encoder) {
        this.identity = identity;
        this.catalog = catalog;
        this.enrollment = enrollment;
        this.credentials = credentials;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (credentials.count() > 0) {
            return; // already seeded
        }

        // 1) the tenant
        Organization org = identity.createOrganization("Acme University");
        TenantContext.set(TenantId.of(org.id()));
        try {
            // 2) people + their roles (membership) + their logins (credential)
            account("admin@acme.test", "Alex Admin", Role.ORG_ADMIN, org.id());
            UUID inst1 = account("instructor@acme.test", "Dr. Ada Rahman", Role.INSTRUCTOR, org.id());
            account("instructor2@acme.test", "Prof. Jon Lindgren", Role.INSTRUCTOR, org.id());
            UUID stu1 = account("student@acme.test", "Maria Okafor", Role.LEARNER, org.id());
            UUID stu2 = account("student2@acme.test", "Sam Chen", Role.LEARNER, org.id());
            UUID stu3 = account("student3@acme.test", "Priya Nair", Role.LEARNER, org.id());
            UUID stu4 = account("student4@acme.test", "Tomás Vega", Role.LEARNER, org.id());

            // 3) catalog
            Course c1 = catalog.createCourse("Multi-Tenant SaaS Architecture");
            Course c2 = catalog.createCourse("Streaming & the Outbox Pattern");
            Course c3 = catalog.createCourse("Production Resilience & SRE");
            catalog.createCourse("Accessible Web Apps (WCAG 2.2)");

            // 4) cohorts + enrollments (the instructor's classes)
            Cohort h1 = enrollment.createCohort(c1.id(), 50);
            Cohort h2 = enrollment.createCohort(c2.id(), 50);
            for (UUID learner : List.of(stu1, stu2, stu3)) {
                enrollment.enroll(h1.id(), learner);
            }
            enrollment.enroll(h2.id(), stu1);
            enrollment.enroll(h2.id(), stu4);

            // silence "unused" for the second instructor reference — both exist as instructors
            assert inst1 != null;
        } finally {
            TenantContext.clear();
        }
    }

    /** Create the domain user + per-tenant role + the login credential. Returns the app-user id. */
    private UUID account(String email, String name, Role role, UUID tenantId) {
        AppUser user = identity.createUser(email, name);
        identity.addMembership(user.id(), role);
        credentials.save(Credential.of(
            email, encoder.encode(DEMO_PASSWORD), role, tenantId, user.id(), name));
        return user.id();
    }
}
