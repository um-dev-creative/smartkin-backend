package com.umdc.smartkin.security;

import com.umdc.commons.constants.keys.AuthKey;
import com.umdc.security.exception.CertificateSecurityException;
import com.umdc.security.jwt.JwtConfigProperties;
import com.umdc.security.service.SessionJwtService;
import com.umdc.smartkin.security.revocation.RevocationChecker;
import com.umdc.smartkin.security.revocation.SessionValidationUnavailableException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.RequiredTypeException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// SmartKin's [SessionJwtService], the contract the security library's `SessionJwtInterceptor` calls for every request under
/// `umdc.api.endpoint`. Same model as zerobound-rest and mercury: the session **is the backbone-rest session token** the BFF got at sign-in,
/// sent as is in the `session-token` header. SmartKin never issues sessions of its own; sign-in, renewal and sign-out are backbone's.
///
/// A token is accepted only if its HMAC signature verifies with `umdc.security.jwt.secret` (the secret shared with backbone-rest), it has
/// not expired (an `exp` is required), `type` is `session-token`, it carries a `jti`, `uid` is a UUID, `aid` is the SmartKin application
/// and the `iss`/`aud` match [SessionClaimPolicy] when configured. [#isValid] also asks the [RevocationChecker] whether backbone-rest
/// revoked the `jti`; [#getVerifiedUid] is the same local check without the revocation list.
@Service
public class SessionJwtServiceImpl implements SessionJwtService {

    /// The claim with the backbone user id.
    public static final String USER_ID = "uid";
    /// The claim with the application the session was opened for (backbone-rest's `AuthKey.APPLICATION_ID`).
    public static final String APPLICATION_ID = "aid";

    private final JwtConfigProperties jwtConfigProperties;
    private final SessionClaimPolicy claimPolicy;
    private final RevocationChecker revocationChecker;
    private final SecretKey key;

    /// Builds the service.
    ///
    /// @param jwtConfigProperties the secret shared with backbone-rest and the lifetime used by [#generateSessionToken]
    /// @param claimPolicy         the application, issuer and audience a SmartKin session must carry
    /// @param revocationChecker   the check against backbone-rest's deny-list of revoked tokens
    public SessionJwtServiceImpl(JwtConfigProperties jwtConfigProperties, SessionClaimPolicy claimPolicy, RevocationChecker revocationChecker) {
        this.jwtConfigProperties = jwtConfigProperties;
        this.claimPolicy = claimPolicy;
        this.revocationChecker = revocationChecker;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfigProperties.getSecret()));
    }

    /// Mints a session token the way backbone-rest does (for tests and tools); SmartKin itself never issues them.
    ///
    /// @param username   the subject
    /// @param parameters extra claims; they cannot override `jti`, `type`, `aid`, `iat` or `exp`
    /// @return the signed token
    @Override
    public String generateSessionToken(String username, Map<String, String> parameters) {
        Instant now = Instant.now();
        Map<String, Object> claims = new ConcurrentHashMap<>();
        // optional first, so callers cannot override the security-sensitive claims below
        if (Objects.nonNull(parameters)) {
            claims.putAll(parameters);
        }
        claims.put(AuthKey.JTI.value, UUID.randomUUID().toString());
        claims.put(AuthKey.TYPE.value, SESSION_TOKEN_KEY);
        claims.put(APPLICATION_ID, claimPolicy.applicationId().toString());
        claims.put(AuthKey.IAT.value, now.getEpochSecond());
        claims.put("exp", now.plusMillis(jwtConfigProperties.getExpirationMs()).getEpochSecond());
        var builder = Jwts.builder().claims(claims).subject(username);
        if (StringUtils.hasText(claimPolicy.issuer())) {
            builder.issuer(claimPolicy.issuer());
        }
        if (StringUtils.hasText(claimPolicy.audience())) {
            builder.audience().add(claimPolicy.audience());
        }
        return builder.signWith(key).compact();
    }

    /// The claims of a token whose signature verifies.
    ///
    /// @param token the token
    /// @return its claims
    /// @throws CertificateSecurityException when the token is malformed, expired or signed with another key
    @Override
    public Claims getTokenClaims(String token) throws CertificateSecurityException {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            throw new CertificateSecurityException(e.getMessage(), e);
        }
    }

    /// The subject of a token whose signature verifies.
    ///
    /// @param token the token
    /// @return its subject
    @Override
    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    /// Called by `SessionJwtInterceptor` for every API request: the local checks of [#getVerifiedUid], then "has backbone-rest revoked
    /// this `jti`?".
    ///
    /// @param token the session token
    /// @return `true` when the token is a live SmartKin session
    /// @throws SessionValidationUnavailableException when the revocation list cannot be consulted (the request gets a 503, never access)
    @Override
    public boolean isValid(String token) {
        return verify(token).map(v -> !revocationChecker.isRevoked(v.jti())).orElse(false);
    }

    /// The `uid` of a locally acceptable token (see the class documentation), or empty for anything missing, expired, tampered,
    /// mis-typed, from another issuer or audience, of another application, without `jti` or without a UUID `uid`. It does not consult the
    /// revocation list: the interceptor already did before any controller code runs.
    ///
    /// @param token the session token
    /// @return the user id
    public Optional<UUID> getVerifiedUid(String token) {
        return verify(token).map(Verified::uid);
    }

    /// What a locally acceptable token yields.
    private record Verified(String jti, UUID uid) {
    }

    private Optional<Verified> verify(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            Claims claims = getTokenClaims(token);
            String uid = claims.get(USER_ID, String.class);
            boolean acceptable = SESSION_TOKEN_KEY.equals(claims.get(AuthKey.TYPE.value))
                    && Objects.nonNull(claims.getExpiration())
                    && StringUtils.hasText(claims.getId())
                    && Objects.nonNull(uid)
                    && issuerMatches(claims)
                    && audienceMatches(claims)
                    && isForThisApplication(claims);
            return acceptable ? Optional.of(new Verified(claims.getId(), UUID.fromString(uid))) : Optional.empty();
        } catch (CertificateSecurityException | IllegalArgumentException | RequiredTypeException e) {
            return Optional.empty();
        }
    }

    /// A session of another application (or one that names none) is not a SmartKin session.
    private boolean isForThisApplication(Claims claims) {
        return claimPolicy.applicationId().toString().equalsIgnoreCase(claims.get(APPLICATION_ID, String.class));
    }

    private boolean issuerMatches(Claims claims) {
        return !StringUtils.hasText(claimPolicy.issuer()) || claimPolicy.issuer().equals(claims.getIssuer());
    }

    private boolean audienceMatches(Claims claims) {
        return !StringUtils.hasText(claimPolicy.audience())
                || (Objects.nonNull(claims.getAudience()) && claims.getAudience().contains(claimPolicy.audience()));
    }
}
