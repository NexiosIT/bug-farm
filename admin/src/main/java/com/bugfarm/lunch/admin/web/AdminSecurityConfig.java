package com.bugfarm.lunch.admin.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Catch-all security chain: {@code /api/admin/**} needs the admin session, everything
 * else (the static frontend) is public. The Teams webhook has its own chain at
 * {@code @Order(1)} in the orders module.
 */
@Configuration
public class AdminSecurityConfig {

    static final String LOGIN_URL = "/api/admin/login";

    @Bean
    @Order(2)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_URL).permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                // The SPA reads the XSRF-TOKEN cookie and sends it back as X-XSRF-TOKEN.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler((req, res, auth) -> {
                            // Login rotated the token and cleared the cookie; loading the new
                            // token writes the new XSRF-TOKEN cookie on this response.
                            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
                            if (token != null) {
                                token.getToken();
                            }
                            res.setStatus(HttpStatus.OK.value());
                        })
                        .failureHandler((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout
                        .logoutUrl("/api/admin/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            // Logout cleared the cookie; hand out a fresh token for the next login.
                            csrfTokens.saveToken(csrfTokens.generateToken(req), req, res);
                            new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)
                                    .onLogoutSuccess(req, res, auth);
                        }))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The one admin account. A blank hash means nobody can log in. */
    @Bean
    public UserDetailsService adminUser(@Value("${lunch.admin.username:admin}") String username,
                                        @Value("${lunch.admin.password-hash:}") String passwordHash) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordHash)
                .roles("ADMIN")
                .build());
    }

    /** Loads the deferred CSRF token on every request, so the XSRF-TOKEN cookie is always set. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
