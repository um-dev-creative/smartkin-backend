package com.umdc.smartkin;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The security library's `SecurityConfig` (Keycloak-style resource server) must stay out of the component scan, and nothing else of the
/// library may be dropped: the interceptor, its web configurer and the properties classes authenticate the API.
class SmartKinBackendApplicationScanTest {

    private final ComponentScan scan = SmartKinBackendApplication.class.getAnnotation(ComponentScan.class);

    private Pattern libraryExclusion() {
        return Arrays.stream(scan.excludeFilters())
                .filter(filter -> filter.type() == FilterType.REGEX)
                .map(filter -> Pattern.compile(filter.pattern()[0]))
                .findFirst().orElseThrow();
    }

    @Test
    void scansTheSamePackagesAsBefore() {
        assertEquals(java.util.List.of("com.umdc.commons.services", "com.umdc.smartkin", "com.umdc.security"),
                Arrays.asList(scan.basePackages()));
    }

    @Test
    void excludesOnlyTheLibrarysResourceServerChain() {
        Pattern exclusion = libraryExclusion();
        assertTrue(exclusion.matcher("com.umdc.security.config.SecurityConfig").matches());
        assertFalse(exclusion.matcher("com.umdc.security.config.SessionJwtWebConfigurer").matches());
        assertFalse(exclusion.matcher("com.umdc.security.interceptor.SessionJwtInterceptor").matches());
        assertFalse(exclusion.matcher("com.umdc.smartkin.config.SessionTokenSecurityConfig").matches());
    }
}
