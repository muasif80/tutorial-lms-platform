package com.scholr.lms.web;

import java.io.IOException;
import java.util.UUID;

import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the tenant for each request from the {@code X-Tenant-Id} header and puts
 * it in {@link TenantContext}. In a real system this comes from the authenticated
 * principal (JWT/SSO claim), never from a client-supplied header — but the mechanism
 * is identical. Always cleared in a finally block so the thread can be reused safely.
 */
@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("X-Tenant-Id");
        try {
            if (header != null && !header.isBlank()) {
                TenantContext.set(TenantId.of(UUID.fromString(header)));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
