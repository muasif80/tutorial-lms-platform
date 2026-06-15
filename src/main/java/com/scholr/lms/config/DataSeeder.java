package com.scholr.lms.config;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.scholr.lms.assessment.AssessmentService;
import com.scholr.lms.assessment.domain.Assessment;
import com.scholr.lms.assessment.domain.QuestionType;
import com.scholr.lms.billing.BillingService;
import com.scholr.lms.billing.domain.Plan;
import com.scholr.lms.auth.domain.Credential;
import com.scholr.lms.auth.internal.CredentialRepository;
import com.scholr.lms.catalog.AuthoringService;
import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.catalog.domain.Section;
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
    private final AuthoringService authoring;
    private final EnrollmentService enrollment;
    private final AssessmentService assessment;
    private final BillingService billing;
    private final CredentialRepository credentials;
    private final PasswordEncoder encoder;

    public DataSeeder(IdentityService identity, CatalogService catalog, AuthoringService authoring,
                      EnrollmentService enrollment, AssessmentService assessment, BillingService billing,
                      CredentialRepository credentials, PasswordEncoder encoder) {
        this.identity = identity;
        this.catalog = catalog;
        this.authoring = authoring;
        this.enrollment = enrollment;
        this.assessment = assessment;
        this.billing = billing;
        this.credentials = credentials;
        this.encoder = encoder;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (credentials.count() > 0) {
            return; // already seeded
        }

        // 1) the tenant. NOTE: deliberately NOT wrapped in a single @Transactional — Hibernate's
        // @TenantId resolver fixes the session's tenant when the session opens, so the tenant context
        // must be set BEFORE the tenant-scoped saves begin (each service call opens its own session).
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

            // 3) catalog — courses authored as ordered lessons and published so learners can find them
            Course c1 = catalog.createCourse("Multi-Tenant SaaS Architecture");
            lessons(c1, "Pool, silo & bridge tenancy models",
                        "The tenant_id discriminator and Hibernate @TenantId",
                        "PostgreSQL Row-Level Security as a second wall",
                        "Resolving the tenant from identity, not a trusted header");
            catalog.publish(c1.id());

            Course c2 = catalog.createCourse("Streaming & the Outbox Pattern");
            lessons(c2, "Why dual writes drift", "The transactional outbox",
                        "An at-least-once relay", "Idempotent consumers and exactly-once effects");
            catalog.publish(c2.id());

            Course c3 = catalog.createCourse("Production Resilience & SRE");
            lessons(c3, "Liveness vs readiness probes", "Containing cascading failure",
                        "SLOs, error budgets and alerting");
            catalog.publish(c3.id());

            Course c4 = catalog.createCourse("Accessible Web Apps (WCAG 2.2)");
            lessons(c4, "The four POUR principles", "Keyboard and focus management",
                        "Colour, contrast and motion");
            catalog.publish(c4.id());

            // 4) cohorts (a class per course) + the instructor's existing enrollments
            Cohort h1 = enrollment.createCohort(c1.id(), 50);
            Cohort h2 = enrollment.createCohort(c2.id(), 50);
            enrollment.createCohort(c3.id(), 50);
            enrollment.createCohort(c4.id(), 50);
            for (UUID learner : List.of(stu1, stu2, stu3)) {
                enrollment.enroll(h1.id(), learner);
            }
            enrollment.enroll(h2.id(), stu1);
            enrollment.enroll(h2.id(), stu4);

            // 5) an auto-graded assessment on the first course, so the learning flow ends in a real grade
            seedQuiz(c1.id());

            // 5b) rich, block-authored sections on the first lesson of course 1, so the lesson view shows
            //     the full content experience (headings, lists, quote, video embed, table, code) on boot.
            UUID firstLesson = catalog.lessons(c1.id()).get(0).id();
            seedRichSections(firstLesson);

            // 6) billing — plans + a couple of subscriptions, so the admin billing console has real rows.
            //    Payment is off in the demo; these are recorded directly (the entry point Part 7 uses).
            Plan monthly = billing.createPlan("All-Access (Monthly)", "all-access", Plan.Interval.MONTH, 4900);
            Plan annual = billing.createPlan("All-Access (Annual)", "all-access", Plan.Interval.YEAR, 49000);
            billing.subscribe(stu1, monthly.id(), "sub_demo_maria", false); // ACTIVE
            billing.subscribe(stu4, annual.id(), "sub_demo_tomas", true);   // TRIALING

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

    /** Author a course's lessons in order, with a short stand-in body for each. */
    private void lessons(Course course, String... titles) {
        for (String title : titles) {
            catalog.addLesson(course.id(), title, "In this lesson: " + title
                + ". A concise, self-contained unit the learner works through and marks complete.");
        }
    }

    /** Author two block-editor sections on a lesson (rendered + sanitized via AuthoringService). */
    private void seedRichSections(UUID lessonId) {
        String overview = """
            {"blocks":[
              {"type":"header","data":{"text":"Why tenant isolation is non-negotiable","level":2}},
              {"type":"paragraph","data":{"text":"In a multi-tenant SaaS, one shared database serves every customer. The whole business rests on one invariant: <b>no tenant can ever see another tenant's data</b>."}},
              {"type":"list","data":{"style":"unordered","items":["A data leak is an existential failure, not a cosmetic one","Isolation must be enforced by construction, not by remembering to filter","Defence in depth: the app layer and the database each enforce it independently"]}},
              {"type":"quote","data":{"text":"The cheapest cross-tenant leak still costs you every customer's trust.","caption":"Scholr post-mortem"}},
              {"type":"embed","data":{"service":"youtube","embed":"https://www.youtube.com/embed/MfcBYr5KZh4","width":580,"height":320,"caption":"Multi-tenancy, in two minutes"}}
            ]}""";
        String models = """
            {"blocks":[
              {"type":"header","data":{"text":"The three tenancy models","level":2}},
              {"type":"paragraph","data":{"text":"There are three classic ways to isolate tenants, trading isolation against cost and operational simplicity."}},
              {"type":"table","data":{"withHeadings":true,"content":[["Model","Isolation","Cost"],["Silo (database per tenant)","Strongest","Highest"],["Bridge (schema per tenant)","Strong","Medium"],["Pool (shared schema + tenant_id)","Enforced in software","Lowest"]]}},
              {"type":"paragraph","data":{"text":"Scholr uses the <b>pool</b> model: a tenant_id discriminator plus PostgreSQL Row-Level Security as a second wall."}},
              {"type":"code","data":{"code":"CREATE POLICY tenant_isolation ON courses\\n  USING (tenant_id = current_setting('app.tenant_id')::uuid);"}}
            ]}""";
        seedSection(lessonId, "Overview", overview);
        seedSection(lessonId, "The three tenancy models", models);
    }

    private void seedSection(UUID lessonId, String title, String contentJson) {
        Section s = authoring.addSection(lessonId, title);
        authoring.saveSection(s.id(), title, contentJson); // renders + sanitizes the block JSON to HTML
    }

    /** A small auto-graded quiz: one single-choice, one multiple-choice, one short-text question. */
    private void seedQuiz(UUID courseId) {
        Assessment quiz = assessment.createAssessment(courseId, "Multi-Tenancy Knowledge Check", 3, 0);
        assessment.addQuestion(quiz.id(), QuestionType.SINGLE_CHOICE,
            "Which column enforces tenant isolation on every tenant-scoped table?", 1,
            List.of("tenant_id", "user_id", "org_name", "schema_name"), Set.of("0"));
        assessment.addQuestion(quiz.id(), QuestionType.MULTIPLE_CHOICE,
            "Which of these are real isolation layers in this architecture? (select all that apply)", 2,
            List.of("Hibernate @TenantId filter", "PostgreSQL Row-Level Security",
                    "A trusted X-Tenant-Id header", "Filtering only in the browser"),
            Set.of("0", "1"));
        assessment.addQuestion(quiz.id(), QuestionType.SHORT_TEXT,
            "Which JPA annotation provides the optimistic lock guarding the seat invariant? "
                + "(one word, include the @)", 1,
            List.of(), Set.of("@version"));
    }
}
