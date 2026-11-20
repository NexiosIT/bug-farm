package com.bugfarm.lunch.orders.teams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the Teams webhook. {@code /api/teams/**} is outside the admin login and has no CSRF;
 * the HMAC check in the controller is what protects it.
 */
@Configuration
public class TeamsSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain teamsSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/teams/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    HmacVerifier hmacVerifier(@Value("${lunch.teams.security-token:}") String securityToken) {
        return new HmacVerifier(securityToken);
    }
}
