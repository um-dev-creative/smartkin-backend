package com.umdc.smartkin.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/// What a backbone session token must carry to be a SmartKin session, on top of the HMAC signature with `umdc.security.jwt.secret`
/// (`APP_TOKEN_SECRET`, the Base64 secret shared with backbone-rest). Same policy as zerobound-rest's `JwtProperties`.
///
/// - `applicationId`: the SmartKin application in backbone-rest (`umdc.smartkin.application-id`, the same value the BFF signs in with
///   as `BACKBONE_APPLICATION_ID`). backbone-rest writes the application a session was opened for in the `aid` claim and every UMDC
///   service shares the signing secret, so without this check a session of any other application would be accepted. Mandatory.
/// - `issuer` / `audience`: what backbone-rest stamps on its tokens (`JWT_ISSUER`, `JWT_AUDIENCE`); a blank value disables that check.
///
/// @param issuer        the expected `iss`, or blank
/// @param audience      the expected `aud`, or blank
/// @param applicationId the expected `aid`
@Component
public record SessionClaimPolicy(String issuer, String audience, UUID applicationId) {

    /// Validates the policy.
    ///
    /// @param issuer        the expected `iss`, or blank
    /// @param audience      the expected `aud`, or blank
    /// @param applicationId the expected `aid`
    public SessionClaimPolicy {
        Objects.requireNonNull(applicationId, "umdc.smartkin.application-id is required: the id of the SmartKin application in backbone-rest");
    }

    /// Spring constructor: reads the policy from the configuration.
    ///
    /// @param issuer        `umdc.smartkin.security.jwt.issuer` (`JWT_ISSUER`, `backbone-rest` by default)
    /// @param audience      `umdc.smartkin.security.jwt.audience` (`JWT_AUDIENCE`, `backbone-rest-client` by default)
    /// @param applicationId `umdc.smartkin.application-id`
    @Autowired
    public SessionClaimPolicy(@Value("${umdc.smartkin.security.jwt.issuer:${JWT_ISSUER:backbone-rest}}") String issuer,
                              @Value("${umdc.smartkin.security.jwt.audience:${JWT_AUDIENCE:backbone-rest-client}}") String audience,
                              @Value("${umdc.smartkin.application-id}") String applicationId) {
        this(issuer, audience, UUID.fromString(applicationId));
    }
}
