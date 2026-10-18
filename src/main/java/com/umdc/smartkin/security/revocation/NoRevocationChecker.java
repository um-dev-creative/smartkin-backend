package com.umdc.smartkin.security.revocation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/// Used when `prx.security.revocation.enabled=false`: no token is ever considered revoked.
@Component
@ConditionalOnProperty(prefix = "prx.security.revocation", name = "enabled", havingValue = "false")
public class NoRevocationChecker implements RevocationChecker {

    public NoRevocationChecker() {
        // Default constructor
    }

    @Override
    public boolean isRevoked(String jti) {
        return false;
    }
}
