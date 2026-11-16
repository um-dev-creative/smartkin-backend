package com.umdc.smartkin.util;

import feign.FeignException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/// What to answer the caller when a call to backbone or mercury fails.
public final class UpstreamFailure {

    private UpstreamFailure() {
        // Utility class
    }

    /// The status to answer when backbone or mercury fails. A refusal of our own credential (401/403) is not the caller's fault and must not read
    /// as an expired session, and no status at all (host unreachable, TLS failure) is a bad gateway; any other status is relayed.
    ///
    /// @param e the failure of the Feign call
    /// @return the HTTP status code to answer
    public static int statusToAnswer(FeignException e) {
        int status = e.status();
        if (status < BAD_REQUEST.value() || status == UNAUTHORIZED.value() || status == FORBIDDEN.value()) {
            return BAD_GATEWAY.value();
        }
        return status;
    }
}
