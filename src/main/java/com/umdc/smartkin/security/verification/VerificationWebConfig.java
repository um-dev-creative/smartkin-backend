package com.umdc.smartkin.security.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/// Registers [VerifiedSessionInterceptor] on the whole API, after the session interceptor of the security library (which validates the
/// token) so that the claim is only read from a genuine token. `umdc.smartkin.verification.required=false` switches it off.
@Configuration(proxyBeanMethods = false)
public class VerificationWebConfig implements WebMvcConfigurer {

    private final String apiPath;
    private final boolean required;

    /// @param apiPath  the base path of the API (`umdc.api.endpoint`)
    /// @param required whether an unverified session is refused
    public VerificationWebConfig(@Value("${umdc.api.endpoint}") String apiPath,
                                 @Value("${umdc.smartkin.verification.required:true}") boolean required) {
        this.apiPath = apiPath;
        this.required = required;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new VerifiedSessionInterceptor(apiPath, required)).addPathPatterns(apiPath + "/**")
                .order(Ordered.LOWEST_PRECEDENCE);
    }
}
