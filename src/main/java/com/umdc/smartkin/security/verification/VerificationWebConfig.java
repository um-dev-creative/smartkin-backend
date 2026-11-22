package com.umdc.smartkin.security.verification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/// Registers [VerifiedSessionInterceptor] on the whole API, after the session interceptor of the security library (which validates the
/// token) so that the `uid` is only read from a genuine token. `umdc.smartkin.verification.required=false` switches it off.
///
/// [VerificationStatus] is taken through an [ObjectProvider] and resolved on the first request, never while the context starts: it needs
/// the Feign `MercuryClient`, whose creation asks for the MVC infrastructure (`mvcResourceUrlProvider`), which in turn is built from
/// every `WebMvcConfigurer`, this one included. Injecting it directly closed that loop and the application failed to start with
/// `BeanCurrentlyInCreationException` (circular reference).
@Configuration(proxyBeanMethods = false)
public class VerificationWebConfig implements WebMvcConfigurer {

    private final String apiPath;
    private final boolean required;
    private final ObjectProvider<VerificationStatus> verificationStatus;

    /// @param apiPath            the base path of the API (`umdc.api.endpoint`)
    /// @param required           whether an unverified session is refused
    /// @param verificationStatus whether a user confirmed the e-mail code (mercury), looked up lazily
    public VerificationWebConfig(@Value("${umdc.api.endpoint}") String apiPath,
                                 @Value("${umdc.smartkin.verification.required:true}") boolean required,
                                 ObjectProvider<VerificationStatus> verificationStatus) {
        this.apiPath = apiPath;
        this.required = required;
        this.verificationStatus = verificationStatus;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new VerifiedSessionInterceptor(apiPath, required, verificationStatus::getObject)).addPathPatterns(apiPath + "/**")
                .order(Ordered.LOWEST_PRECEDENCE);
    }
}
