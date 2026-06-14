package com.scholr.lms.auth;

import java.io.IOException;

import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the request's tenant context from the <em>authenticated</em> user, replacing the trusted
 * {@code X-Tenant-Id} header with a real, login-derived tenant. After Spring Security has authenticated
 * the request, this filter reads the {@link UserPrincipal} and pins {@link TenantContext} to that user's
 * tenant for the duration of the request, clearing it afterward so threads never leak tenant state.
 *
 * <p>This is the link that makes isolation trustworthy end to end: a logged-in instructor at Acme can
 * only ever see Acme's data, because the tenant comes from their authenticated identity, not a header a
 * client could forge.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TenantPrincipalFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean set = false;
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            TenantContext.set(TenantId.of(principal.tenantId()));
            set = true;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (set) {
                TenantContext.clear();
            }
        }
    }
}
