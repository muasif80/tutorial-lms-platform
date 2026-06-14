package com.scholr.lms.auth;

import com.scholr.lms.auth.internal.CredentialRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a {@link UserPrincipal} by email for Spring Security's form-login authentication. Reads the
 * global {@code credentials} table, so it works before any tenant context is established.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final CredentialRepository credentials;

    public AppUserDetailsService(CredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return credentials.findByEmail(email)
            .map(UserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("no account for " + email));
    }
}
