package com.umdc.smartkin.client.backbone;

import com.umdc.smartkin.client.backbone.to.ManagedClientToken;
import com.umdc.smartkin.interceptor.TrustedFeignConfigurer;
import com.umdc.smartkin.client.backbone.to.ManagedClientTokenRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/// Feign client of backbone-rest's public managed-client token endpoint. Its `configuration` is [TrustedFeignConfigurer] on purpose: it
/// only makes the client trust the internal CA (without it the call fails with `PKIX path building failed`) and adds no interceptor,
/// because the endpoint takes the client credentials in the body, so the OAuth Bearer that [BackboneClient] adds to every call must not be sent.
@FeignClient(name = "backboneManagedClient", url = "${umdc.clients.backbone.url}", configuration = TrustedFeignConfigurer.class)
public interface BackboneManagedClient {

    /// Issues the Backbone M2M token of a managed client.
    ///
    /// @param request client id, secret and scopes
    /// @return the token and its life in seconds
    @PostMapping("/api/v1/managed-clients/token")
    ManagedClientToken token(@RequestBody ManagedClientTokenRequest request);
}
