package com.umdc.smartkin.interceptor;

import com.umdc.security.properties.AuthProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class MercuryFeignConfigurer extends BackendFeignConfigurer {

    public MercuryFeignConfigurer(AuthProperties authProperties) {
        super(authProperties.getClients().stream()
                .filter(authProperties1 -> authProperties1.getId()
                        .equalsIgnoreCase(MERCURY_ID)).toList().getFirst());
    }

    @Bean("mercuryFeignClientInterceptor")
    @Override
    RequestInterceptor requestInterceptor() {
        // Mercury is called with its own `session-token` header (see MercuryServiceLogin) and checks no Authorization bearer. The one this
        // interceptor used to add came from the Keycloak-style token server, which no longer exists (404) and made every call fail.
        return template -> { };
    }
}
