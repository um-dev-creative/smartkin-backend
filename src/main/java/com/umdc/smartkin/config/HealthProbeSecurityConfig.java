package com.umdc.smartkin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/// Lets the container HEALTHCHECK and Kubernetes probes read `/actuator/health/**` without a token.
///
/// The security library's filter chain makes everything but Swagger require a bearer token, so without this chain the probe answers 401
/// and the container is reported unhealthy. This chain matches only the health endpoints and runs first; every other request (including
/// the rest of `/actuator`, which is not exposed anyway) still goes through the library's chain. Health shows no details
/// (`management.endpoint.health.show-details: never`), so nothing sensitive is returned.
@Configuration(proxyBeanMethods = false)
public class HealthProbeSecurityConfig {

    /// Not meant to be instantiated by callers: Spring builds it to read the bean methods.
    public HealthProbeSecurityConfig() {
        // Configuration holder
    }

    /// @param http the Spring Security builder
    /// @return a chain that permits the health endpoints and nothing else
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain healthProbeFilterChain(HttpSecurity http) {
        return http
                .securityMatcher("/actuator/health", "/actuator/health/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
