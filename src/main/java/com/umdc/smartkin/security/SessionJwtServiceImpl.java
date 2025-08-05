package com.umdc.smartkin.security;

import com.prx.commons.constants.keys.AuthKey;
import com.prx.security.exception.CertificateSecurityException;
import com.prx.security.jwt.JwtConfigProperties;
import com.prx.security.service.SessionJwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.prx.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/**
 * Implementation of the SessionJwtService interface providing JWT-related operations.
 * This class is responsible for generating and validating session tokens
 * and extracting information from JWT tokens.
 */
@Service
public class SessionJwtServiceImpl implements SessionJwtService {

    private static final Logger logger = LoggerFactory.getLogger(SessionJwtServiceImpl.class);
    private final JwtConfigProperties jwtConfigProperties;
    private final SecretKey key;

    /**
     * Constructor to initialize SessionJwtService with JwtConfigProperties.
     *
     * @param jwtConfigProperties the configuration properties for JWT
     */
    public SessionJwtServiceImpl(JwtConfigProperties jwtConfigProperties) {
        this.jwtConfigProperties = jwtConfigProperties;
        this.key = generateKey();
    }

    /**
     * Generates a session token for a user based on the given username and additional parameters.
     * The generated token contains standard claims as well as any optional parameters provided.
     *
     * @param username the username for which the session token is being generated
     * @param parameters a map of optional parameters to include in the token claims; can be null or empty
     * @return the generated session token as a string
     */
    public String generateSessionToken(String username, Map<String, String> parameters) {
        Map<String, Object> claims = new ConcurrentHashMap<>();
        // Required
        claims.put(AuthKey.JTI.value, UUID.randomUUID().toString());
        claims.put(AuthKey.TYPE.value, SESSION_TOKEN_KEY);
        claims.put(AuthKey.IAT.value, new Date());
        // Optional
        if (Objects.nonNull(parameters) && !parameters.isEmpty()) {
            claims.putAll(parameters);
        }

        return Jwts.builder().claims(claims).subject(username).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + jwtConfigProperties.getExpirationMs())).signWith(key).compact();
    }

    /**
     * Extracts and returns the claims contained within the provided JWT token.
     * Verifies the token using the configured secret key and parses the claims from it.
     *
     * @param token the JWT token from which claims are to be extracted
     * @return the claims contained within the token
     * @throws CertificateSecurityException if there is an error during token verification or parsing
     */
    @Override
    public Claims getTokenClaims(String token) throws CertificateSecurityException {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            throw new CertificateSecurityException(e.getMessage(), e);
        }
        return claims;
    }

    /**
     * Extracts the username (subject) from the given JWT token.
     *
     * @param token the JWT token from which the username is to be extracted
     * @return the username extracted from the token
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    /**
     * Generates a secret key used for signing and verifying JWT tokens
     * by decoding the secret string from the configuration properties.
     *
     * @return the generated secret key
     */
    private SecretKey generateKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfigProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

}
