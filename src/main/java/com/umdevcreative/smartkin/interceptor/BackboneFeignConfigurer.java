package com.umdevcreative.smartkin.interceptor;

import com.prx.security.properties.AuthProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configures a Feign client for the Backbone service.
 * This class extends the {@link BackendFeignConfigurer} to inherit and override
 * necessary configurations for the Backbone service.
 *
 * The BackboneFeignConfigurer sets up a specific {@link RequestInterceptor} bean
 * that adds authorization headers and other configurations required for requests
 * to the Backbone service.
 *
 * The configuration relies on {@link AuthProperties} to locate the client configuration
 * for the Backbone service by matching its ID with the constant BACKBONE_ID.
 */
public class BackboneFeignConfigurer extends BackendFeignConfigurer {

    /**
     * Constructs an instance of {@code BackboneFeignConfigurer}.
     * This constructor initializes the configuration for the Backbone service
     * by identifying the appropriate client properties using the provided {@code AuthProperties}.
     * It filters the list of clients within {@code AuthProperties} to find the one
     * matching the constant {@code BACKBONE_ID}, and passes it to the parent class
     * {@link BackendFeignConfigurer}.
     *
     * @param authProperties the authentication properties containing client configurations
     *                        to locate the Backbone client configuration
     */
    public BackboneFeignConfigurer(AuthProperties authProperties) {
        super(authProperties.getClients().stream()
                .filter(authProperties1 -> authProperties1.getId()
                        .equalsIgnoreCase(BACKBONE_ID)).findFirst().orElse(null));
    }

    /**
     * Configures and provides a custom request interceptor bean named {@code backboneFeignClientInterceptor}.
     * This method overrides the parent class implementation to tailor the Feign client's behavior for
     * the Backbone service. It uses the {@link #createInterceptor()} method to construct a {@link RequestInterceptor}
     * that adds authorization headers and handles other necessary configurations for requests.
     *
     * @return a {@link RequestInterceptor} that customizes the Feign client's request behavior, including
     *         adding authorization headers and enabling optional request tracing for debugging purposes.
     */
    @Bean("backboneFeignClientInterceptor")
    @Override
    RequestInterceptor requestInterceptor() {
        return createInterceptor();
    }
}
