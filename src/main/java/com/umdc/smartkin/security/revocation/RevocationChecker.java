package com.umdc.smartkin.security.revocation;

/// Tells whether backbone-rest has revoked a session token.
public interface RevocationChecker {

    /// @param jti the `jti` claim of the token
    /// @return `true` when the token is on the deny-list
    /// @throws SessionValidationUnavailableException when the deny-list cannot be consulted; callers must refuse the request
    boolean isRevoked(String jti);
}
