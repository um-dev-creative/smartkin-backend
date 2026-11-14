package com.umdc.smartkin.client.backbone;

import com.umdc.smartkin.client.backbone.to.ManagedClientToken;
import com.umdc.smartkin.client.backbone.to.ManagedClientTokenRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/// Feign client of backbone-rest's public managed-client token endpoint. It has no `configuration` on purpose: the endpoint takes the
/// client credentials in the body, so the OAuth Bearer that [BackboneClient] adds to every call must not be sent.
@FeignClient(name = "backboneManagedClient", url = "${umdc.clients.backbone.url}")
public interface BackboneManagedClient {

    /// Issues the Backbone M2M token of a managed client.
    ///
    /// @param request client id, secret and scopes
    /// @return the token and its life in seconds
    @PostMapping("/api/v1/managed-clients/token")
    ManagedClientToken token(@RequestBody ManagedClientTokenRequest request);
}
