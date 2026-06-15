package com.scholr.lms.web.ui;

import com.scholr.lms.auth.AccountService;
import com.scholr.lms.auth.UserPrincipal;
import com.scholr.lms.catalog.CatalogService;
import com.scholr.lms.identity.domain.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The server-rendered (Thymeleaf) UI for the working platform. Spring Security has already authenticated
 * the request and the {@code TenantPrincipalFilter} has pinned the tenant, so these handlers just read
 * the principal and the tenant-scoped services. The home route lands each role on its own dashboard —
 * the RBAC pillar made visible.
 */
@Controller
public class UiController {

    private final AccountService accounts;
    private final CatalogService catalog;

    public UiController(AccountService accounts, CatalogService catalog) {
        this.accounts = accounts;
        this.catalog = catalog;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Land each authenticated user on the dashboard for their role. */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal UserPrincipal principal) {
        return switch (principal.role()) {
            case ORG_ADMIN -> "redirect:/admin";
            case INSTRUCTOR -> "redirect:/instructor";
            case LEARNER -> "redirect:/learn";
        };
    }

    @GetMapping("/admin")
    public String adminDashboard(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        addPrincipal(model, principal);
        model.addAttribute("admins", accounts.count(Role.ORG_ADMIN));
        model.addAttribute("instructors", accounts.count(Role.INSTRUCTOR));
        model.addAttribute("students", accounts.count(Role.LEARNER));
        model.addAttribute("courseCount", catalog.allCourses().size());
        model.addAttribute("people", accounts.listForCurrentTenant());
        return "admin/dashboard";
    }

    @GetMapping("/instructor")
    public String instructorDashboard(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        addPrincipal(model, principal);
        model.addAttribute("courses", catalog.allCourses());
        model.addAttribute("students", accounts.count(Role.LEARNER));
        return "instructor/dashboard";
    }

    /** Admin · People & Roles — list everyone in the tenant and add new instructors/students. */
    @GetMapping("/admin/people")
    public String people(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        addPrincipal(model, principal);
        model.addAttribute("people", accounts.listForCurrentTenant());
        return "admin/people";
    }

    /** Create an account (the "enroll an instructor or student" action). */
    @PostMapping("/admin/people")
    public String addPerson(@RequestParam String name, @RequestParam String email,
                            @RequestParam String role, @RequestParam String password) {
        if (!accounts.emailTaken(email)) {
            accounts.createAccount(email.trim(), name.trim(), Role.valueOf(role),
                password.isBlank() ? "scholr" : password);
            return "redirect:/admin/people?added";
        }
        return "redirect:/admin/people?dup";
    }

    // --- sections still being built out (Part 14); clean placeholders so navigation never 404s ---
    @GetMapping({"/admin/courses", "/admin/billing", "/admin/reports"})
    public String soon(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        addPrincipal(model, principal);
        model.addAttribute("role", switch (principal.role()) {
            case ORG_ADMIN -> "admin";
            case INSTRUCTOR -> "instructor";
            case LEARNER -> "student";
        });
        return "placeholder";
    }

    private void addPrincipal(Model model, UserPrincipal principal) {
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("roleLabel", switch (principal.role()) {
            case ORG_ADMIN -> "Admin";
            case INSTRUCTOR -> "Instructor";
            case LEARNER -> "Student";
        });
    }
}
