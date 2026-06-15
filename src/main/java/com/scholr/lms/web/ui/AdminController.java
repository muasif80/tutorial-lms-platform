package com.scholr.lms.web.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.scholr.lms.assessment.AssessmentService;
import com.scholr.lms.auth.AccountService;
import com.scholr.lms.auth.UserPrincipal;
import com.scholr.lms.billing.BillingService;
import com.scholr.lms.billing.domain.Plan;
import com.scholr.lms.billing.domain.Subscription;
import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.catalog.domain.Course;
import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.Role;
import com.scholr.lms.learning.LearningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The admin console (Part 14): the organisation-wide view that rolls up what the instructor and student
 * workspaces produce — catalogue oversight, engagement reports, and billing. Every number here is a sum
 * over the same tenant-scoped facts the other roles create, so the admin sees the whole platform at once.
 * Role-gated to {@code ROLE_ADMIN}.
 */
@Controller
public class AdminController {

    private final CatalogService catalog;
    private final EnrollmentService enrollment;
    private final LearningService learning;
    private final AssessmentService assessment;
    private final AccountService accounts;
    private final BillingService billing;
    private final IdentityService identity;

    public AdminController(CatalogService catalog, EnrollmentService enrollment, LearningService learning,
                           AssessmentService assessment, AccountService accounts, BillingService billing,
                           IdentityService identity) {
        this.catalog = catalog;
        this.enrollment = enrollment;
        this.learning = learning;
        this.assessment = assessment;
        this.accounts = accounts;
        this.billing = billing;
        this.identity = identity;
    }

    private void principal(Model model, UserPrincipal p) {
        model.addAttribute("displayName", p.displayName());
        model.addAttribute("roleLabel", "Admin");
    }

    // ---- catalogue oversight ---------------------------------------------------------------------

    @GetMapping("/admin/courses")
    public String courses(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);
        List<CourseRow> rows = new ArrayList<>();
        for (Course c : catalog.allCourses()) {
            List<Cohort> cohorts = enrollment.cohortsForCourse(c.id());
            int enrolled = cohorts.stream().mapToInt(Cohort::enrolledCount).sum();
            int capacity = cohorts.stream().mapToInt(Cohort::capacity).sum();
            rows.add(new CourseRow(c.title(), c.isPublished(), catalog.lessonCount(c.id()),
                cohorts.size(), enrolled, capacity, assessment.assessmentsForCourse(c.id()).size()));
        }
        model.addAttribute("courses", rows);
        return "admin/courses";
    }

    public record CourseRow(String title, boolean published, long lessons, int cohorts,
                            int enrolled, int capacity, int assessments) {
    }

    // ---- org reports -----------------------------------------------------------------------------

    @GetMapping("/admin/reports")
    public String reports(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);

        List<Course> all = catalog.allCourses();
        long published = all.stream().filter(Course::isPublished).count();
        long lessons = all.stream().mapToLong(c -> catalog.lessonCount(c.id())).sum();
        long assessments = all.stream().mapToLong(c -> assessment.assessmentsForCourse(c.id()).size()).sum();

        int cohorts = 0, capacity = 0, enrolled = 0;
        for (Course c : all) {
            for (Cohort h : enrollment.cohortsForCourse(c.id())) {
                cohorts++;
                capacity += h.capacity();
                enrolled += h.enrolledCount();
            }
        }

        model.addAttribute("admins", accounts.count(Role.ORG_ADMIN));
        model.addAttribute("instructors", accounts.count(Role.INSTRUCTOR));
        model.addAttribute("students", accounts.count(Role.LEARNER));
        model.addAttribute("coursesTotal", all.size());
        model.addAttribute("coursesPublished", published);
        model.addAttribute("coursesDraft", all.size() - published);
        model.addAttribute("lessons", lessons);
        model.addAttribute("assessments", assessments);
        model.addAttribute("cohorts", cohorts);
        model.addAttribute("capacity", capacity);
        model.addAttribute("enrolled", enrolled);
        model.addAttribute("fillRate", capacity == 0 ? 0 : (int) Math.round(enrolled * 100.0 / capacity));
        model.addAttribute("completions", learning.totalCompletions());
        return "admin/reports";
    }

    // ---- billing ---------------------------------------------------------------------------------

    @GetMapping("/admin/billing")
    public String billing(@AuthenticationPrincipal UserPrincipal p, Model model) {
        principal(model, p);

        List<PlanRow> plans = new ArrayList<>();
        for (Plan plan : billing.allPlans()) {
            plans.add(new PlanRow(plan.name(), plan.entitlementKey(), plan.interval().name(),
                money(plan.priceCents())));
        }

        List<SubRow> subs = new ArrayList<>();
        long active = 0;
        for (Subscription s : billing.allSubscriptions()) {
            String learner = identity.findUser(s.learnerId()).map(u -> u.name()).orElse("—");
            String plan = billing.findPlan(s.planId()).map(Plan::name).orElse("—");
            String status = s.status().name();
            if (!"CANCELED".equals(status)) {
                active++;
            }
            subs.add(new SubRow(learner, plan, status));
        }

        model.addAttribute("plans", plans);
        model.addAttribute("subs", subs);
        model.addAttribute("activeSubs", active);
        model.addAttribute("activeEntitlements", billing.activeEntitlementCount());
        return "admin/billing";
    }

    public record PlanRow(String name, String entitlementKey, String interval, String price) {
    }

    public record SubRow(String learner, String plan, String status) {
    }

    private static String money(long cents) {
        return "$" + (cents / 100) + (cents % 100 == 0 ? "" : String.format(".%02d", cents % 100));
    }
}
