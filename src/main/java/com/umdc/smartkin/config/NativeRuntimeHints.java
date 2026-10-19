package com.umdc.smartkin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/// Reflection, resource and proxy hints GraalVM needs that the dependencies do not ship themselves.
/// They only matter for the native image (`Dockerfile.native`); on the JVM they are ignored.
/// Same gaps backbone-rest and mercury found for the same shared dependencies.
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeRuntimeHints.Hints.class)
public class NativeRuntimeHints {

    /// Not meant to be instantiated: the class only carries the annotation.
    private NativeRuntimeHints() {
        // Holder of the hints registrar
    }

    private static final MemberCategory[] FULL_ACCESS = {
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.ACCESS_DECLARED_FIELDS
    };

    /// Registers the hints.
    public static class Hints implements RuntimeHintsRegistrar {

        /// Public no-argument constructor required by `@ImportRuntimeHints`.
        public Hints() {
            // Instantiated by Spring AOT
        }

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // spring-cloud-vault-config ships no reachability metadata: without these, binding VaultProperties (and its nested
            // Ssl) silently leaves the trust store empty and the Vault call falls back to the JDK's cacerts.
            hints.reflection().registerType(VaultProperties.class, FULL_ACCESS);
            hints.reflection().registerType(VaultProperties.Ssl.class, FULL_ACCESS);
            // The keystores behind the smartkin-security TLS bundle are read as classpath:<name>.jks; native-image only embeds
            // the resources it is told about.
            hints.resources().registerPattern("*.jks");
            // com.umdc.commons-services autowires a request-scoped HttpServletRequest into a singleton, resolved with a JDK proxy.
            hints.proxies().registerJdkProxy(HttpServletRequest.class);
        }
    }
}
