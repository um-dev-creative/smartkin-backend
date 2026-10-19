package com.umdc.smartkin.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// The probe chain opens only the health endpoints; a stand-in for the library's chain (which requires authentication for
/// everything else) proves that nothing else is opened.
@SpringJUnitWebConfig(HealthProbeSecurityConfigTest.TestApp.class)
class HealthProbeSecurityConfigTest {

    @Configuration
    @EnableWebSecurity
    static class TestApp {
        @Bean
        HealthProbeSecurityConfig probeConfig() {
            return new HealthProbeSecurityConfig();
        }

        @Bean
        SecurityFilterChain probeChain(HttpSecurity http) {
            return new HealthProbeSecurityConfig().healthProbeFilterChain(http);
        }

        @Bean
        @Order(Integer.MAX_VALUE)
        SecurityFilterChain libraryStandIn(HttpSecurity http) {
            return http.authorizeHttpRequests(r -> r.anyRequest().authenticated()).build();
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @RestController
    static class Probe {
        @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/env", "/api/v1/users"})
        String ok() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        // spring-security-test is not a dependency: add the security filter chain by hand
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
    }

    @Test
    void healthEndpointsNeedNoToken() throws Exception {
        mvc().perform(get("/actuator/health")).andExpect(status().isOk());
        mvc().perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void everythingElseStillNeedsAuthentication() throws Exception {
        mvc().perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc().perform(get("/api/v1/users")).andExpect(status().is4xxClientError());
    }
}
