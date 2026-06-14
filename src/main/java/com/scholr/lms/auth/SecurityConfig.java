package com.scholr.lms.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The security and RBAC policy for the working UI. Form login authenticates against the global
 * credentials table; on success the {@link TenantPrincipalFilter} pins the tenant from the authenticated
 * user. URL rules enforce role-based access — the visible pillar:
 *
 * <ul>
 *   <li>{@code /admin/**} → {@code ROLE_ADMIN}</li>
 *   <li>{@code /instructor/**} → {@code ROLE_INSTRUCTOR}</li>
 *   <li>{@code /learn/**} → {@code ROLE_STUDENT}</li>
 * </ul>
 *
 * <p>The REST API ({@code /api/**}), the health probes, the OpenAPI/Swagger docs, the login page, and
 * static assets are permitted; everything else requires authentication. (In production the REST API would
 * be token-secured; it is left open here so the documented API and the earlier parts' flows stay usable.)
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TenantPrincipalFilter tenantPrincipalFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // form posts here are simple; CSRF tokens would be added for a public deployment
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/health", "/contexts", "/actuator/**",
                    "/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/instructor/**").hasRole("INSTRUCTOR")
                .requestMatchers("/learn/**").hasRole("STUDENT")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            // set the tenant context from the authenticated principal, before controllers run
            .addFilterAfter(tenantPrincipalFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
