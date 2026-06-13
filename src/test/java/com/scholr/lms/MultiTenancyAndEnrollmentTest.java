package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.CohortFullException;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 2 guarantees on a real persistence stack (H2): tenant isolation via
 * Hibernate {@code @TenantId}, idempotent enrollment, and the seat invariant — through
 * the public services only (so the module boundaries hold).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MultiTenancyAndEnrollmentTest {

    @Autowired
    private IdentityService identity;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private EnrollmentService enrollment;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void tenants_cannot_see_each_others_data() {
        Organization acme = identity.createOrganization("Acme");
        Organization globex = identity.createOrganization("Globex");

        TenantContext.set(TenantId.of(acme.id()));
        catalog.createCourse("Acme Onboarding");

        TenantContext.set(TenantId.of(globex.id()));
        assertTrue(catalog.allCourses().isEmpty(), "Globex must not see Acme's course");
        catalog.createCourse("Globex 101");
        assertEquals(1, catalog.allCourses().size());

        TenantContext.set(TenantId.of(acme.id()));
        assertEquals(1, catalog.allCourses().size(), "Acme sees only its own course");
        assertEquals("Acme Onboarding", catalog.allCourses().get(0).title());
    }

    @Test
    void enroll_is_idempotent_and_respects_the_seat_invariant() {
        Organization org = identity.createOrganization("Acme");
        TenantContext.set(TenantId.of(org.id()));

        AppUser learner1 = identity.createUser("u1@acme.test", "Learner One");
        AppUser learner2 = identity.createUser("u2@acme.test", "Learner Two");
        Course course = catalog.createCourse("Course");
        Cohort cohort = enrollment.createCohort(course.id(), 1); // a single seat

        var first = enrollment.enroll(cohort.id(), learner1.id());
        var retry = enrollment.enroll(cohort.id(), learner1.id()); // a retry must not duplicate
        assertEquals(first.id(), retry.id());

        assertThrows(CohortFullException.class,
            () -> enrollment.enroll(cohort.id(), learner2.id())); // cohort is full
    }
}
