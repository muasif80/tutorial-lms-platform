package com.scholr.lms.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Auth beans that are needed in <em>any</em> application context — including non-web test contexts — and so
 * must live outside the web-only {@link SecurityConfig}. The {@link PasswordEncoder} is used by
 * {@code AccountService} and the data seeder to hash passwords, independent of the servlet stack.
 */
@Configuration
public class AuthBeans {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
