package com.umdc.smartkin.util;

import org.json.JSONObject;

import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class JwtUtil {
    private static final int MAX_LENGTH = 2;

    private JwtUtil(){
        // Default constructor
    }

    /**
     * Extracts the uid from a JWT token.
     *
     * @param token the JWT token
     * @return the uid if present, otherwise null
     */

    public static UUID getUidFromToken(String token) {
        var tempo = getClaim(token, "uid");
        try {
            return Objects.nonNull(tempo) ? UUID.fromString(tempo) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reads one claim of a JWT as text. The signature is not checked here: the session interceptor of the security library
     * validates the token before any controller or interceptor of SmartKin reads it.
     *
     * @param token the JWT token
     * @param name  the claim name
     * @return the claim as text, or null when the token or the claim is missing or malformed
     */
    public static String getClaim(String token, String name) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < MAX_LENGTH) {
                return null;
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            return new JSONObject(payloadJson).optString(name, null);
        } catch (Exception e) {
            return null;
        }
    }
}
