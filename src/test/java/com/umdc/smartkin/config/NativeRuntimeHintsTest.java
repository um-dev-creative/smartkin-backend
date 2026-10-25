package com.umdc.smartkin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.configuration.TlsProperties;
import org.springframework.cloud.vault.config.VaultKeyValueBackendProperties;
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
    void everyPropertiesTypeBoundDuringConfigImportIsReflectable() {
        for (Class<?> type : new Class<?>[] {VaultKeyValueBackendProperties.class, ConfigClientProperties.class, ConfigClientProperties.Credentials.class,
                TlsProperties.class}) {
            assertTrue(RuntimeHintsPredicates.reflection().onType(type)
                    .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints), type.getName());
        }
    }

    @Test
    void theKeystoresAreEmbeddedAsResources() {
        assertTrue(RuntimeHintsPredicates.resource().forResource("smartkin.jks").test(hints));
        assertTrue(RuntimeHintsPredicates.resource().forResource("umdc-truststore.jks").test(hints));
    }

    @Test
    void identifierArraysHibernateInstantiatesAreReflectable() {
        for (Class<?> array : new Class<?>[] {UUID[].class, Long[].class, Integer[].class, String[].class}) {
            assertTrue(RuntimeHintsPredicates.reflection().onType(array).test(hints), array.getName());
        }
    }

    @Test
    void theSessionProxyIsRegisteredToo() {
        assertTrue(RuntimeHintsPredicates.proxies().forInterfaces(HttpSession.class).test(hints));
    }

    @Test
    void theRequestProxyIsRegistered() {
        assertTrue(RuntimeHintsPredicates.proxies().forInterfaces(HttpServletRequest.class).test(hints));
    }
}
