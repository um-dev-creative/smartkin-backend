package com.umdc.smartkin.security.verification;

import com.umdc.security.properties.AuthProperties;
import com.umdc.security.properties.ClientProperties;
import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/// Starts Spring MVC with the real Feign `MercuryClient`, `VerificationStatus` and `VerificationWebConfig`, the beans that formed the
/// circular reference `mvcResourceUrlProvider -> EnableWebMvcConfiguration -> verificationWebConfig -> verificationStatus -> MercuryClient
/// -> mvcResourceUrlProvider` and stopped the application at start (`BeanCurrentlyInCreationException`).
class VerificationWiringTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, FeignAutoConfiguration.class))
            .withUserConfiguration(Feign.class, VerificationStatus.class, VerificationWebConfig.class)
            .withPropertyValues("umdc.api.endpoint=/api/v1", "umdc.clients.mercury.url=https://mercury.invalid");

    @Test
    void theContextStartsWithoutACircularReference() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean("mvcResourceUrlProvider"));
            assertNotNull(context.getBean(VerificationStatus.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = MercuryClient.class)
    static class Feign {

        @Bean
        MercuryTokens mercuryTokens() {
            return mock(MercuryTokens.class);
        }

        @Bean
        AuthProperties authProperties() {
            var client = new ClientProperties();
            client.setId("mercury");
            client.setClientId("mercury-client");
            client.setClientSecret("secret");
            var properties = new AuthProperties();
            properties.setClients(List.of(client));
            return properties;
        }
    }
}
