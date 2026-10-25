package com.umdc.smartkin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.RetryProperties;
import org.springframework.cloud.configuration.TlsProperties;
import org.springframework.cloud.vault.config.VaultKeyValueBackendProperties;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.context.request.WebRequest;

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

    /// Bound during config data import (`spring.config.import`: Vault, the Config Server and the TLS settings both of them use). That
    /// happens before the application context exists, so Spring binds them straight through the `Binder` and AOT never sees them;
    /// without these hints the native image fails at start with `Failed to bind properties under 'spring.cloud.vault'`.
    private static final List<Class<?>> CONFIG_IMPORT_PROPERTIES = List.of(
            VaultProperties.class,
            VaultKeyValueBackendProperties.class,
            ConfigClientProperties.class,
            RetryProperties.class,
            TlsProperties.class);

    /// Registers the hints.
    public static class Hints implements RuntimeHintsRegistrar {

        /// Public no-argument constructor required by `@ImportRuntimeHints`.
        public Hints() {
            // Instantiated by Spring AOT
        }

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // spring-cloud-vault-config and spring-cloud-config-client ship no reachability metadata: without these, binding
            // VaultProperties (and its nested Ssl) silently leaves the trust store empty and the Vault call falls back to the JDK's
            // cacerts. Each type is registered with its nested types (Ssl, Credentials, ...).
            var binding = new BindingReflectionHintsRegistrar();
            for (Class<?> type : CONFIG_IMPORT_PROPERTIES) {
                binding.registerReflectionHints(hints.reflection(), type);
                hints.reflection().registerType(type, FULL_ACCESS);
                for (Class<?> nested : type.getDeclaredClasses()) {
                    binding.registerReflectionHints(hints.reflection(), nested);
                    hints.reflection().registerType(nested, FULL_ACCESS);
                }
            }
            // The keystores behind the smartkin-security TLS bundle are read as classpath:<name>.jks; native-image only embeds
            // the resources it is told about.
            hints.resources().registerPattern("*.jks");
            // Hibernate loads several rows by id with one `= ANY(?)` query and instantiates an array of the identifier type by
            // reflection (MultiIdEntityLoaderArrayParam); without these the EntityManagerFactory fails at start with
            // MissingReflectionRegistrationError "Cannot reflectively instantiate the array class 'java.util.UUID[]'".
            hints.reflection()
                    .registerType(UUID[].class)
                    .registerType(Long[].class)
                    .registerType(Integer[].class)
                    .registerType(String[].class);
            // com.umdc.commons-services autowires a request-scoped HttpServletRequest into a singleton, resolved with a JDK proxy;
            // Spring injects the other web objects the same way.
            for (Class<?> type : List.of(HttpServletRequest.class, HttpServletResponse.class, HttpSession.class, WebRequest.class)) {
                hints.proxies().registerJdkProxy(type);
            }
        }
    }
}
