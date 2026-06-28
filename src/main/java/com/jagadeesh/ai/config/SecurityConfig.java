package com.jagadeesh.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security configuration.
 *
 * <p><b>Why this class is needed:</b> {@code build.gradle.kts} includes
 * {@code spring-boot-starter-security}. The moment that dependency is on
 * the classpath, Spring Security autoconfigures every endpoint to require
 * HTTP Basic auth with a randomly generated password printed to the console
 * at startup — unless a {@link SecurityFilterChain} bean says otherwise.
 * Without this class, every call to {@code /api/rag/**} would return
 * {@code 401 Unauthorized}.</p>
 *
 * <p>This config opens up the RAG endpoints for learning/demo purposes.
 * In a real deployment, replace {@code permitAll()} with actual
 * authentication/authorization rules.</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * Defines the HTTP security rules for the application.
     *
     * @param http the security builder provided by Spring Security
     * @return the configured filter chain
     * @throws Exception if the security configuration cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/rag/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}