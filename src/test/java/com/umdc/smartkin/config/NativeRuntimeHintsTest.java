package com.umdc.smartkin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.cloud.vault.config.VaultProperties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeRuntimeHintsTest {

    private final RuntimeHints hints = new RuntimeHints();

    NativeRuntimeHintsTest() {
        new NativeRuntimeHints.Hints().registerHints(hints, getClass().getClassLoader());
    }

    @Test
    void vaultPropertiesAndTheirNestedSslAreReflectable() {
        assertTrue(RuntimeHintsPredicates.reflection().onType(VaultProperties.class)
                .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS).test(hints));
        assertTrue(RuntimeHintsPredicates.reflection().onType(VaultProperties.Ssl.class)
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS).test(hints));
    }

    @Test
    void theKeystoresAreEmbeddedAsResources() {
        assertTrue(RuntimeHintsPredicates.resource().forResource("smartkin.jks").test(hints));
        assertTrue(RuntimeHintsPredicates.resource().forResource("umdc-truststore.jks").test(hints));
    }

    @Test
    void theRequestProxyIsRegistered() {
        assertTrue(RuntimeHintsPredicates.proxies().forInterfaces(HttpServletRequest.class).test(hints));
    }
}
