package com.umdc.smartkin.security.verification;

import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import com.umdc.smartkin.security.revocation.SessionValidationUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Whether a user confirmed the e-mail code, as mercury records it. The session is backbone's token, which says nothing about it, so this
/// is how SmartKin keeps out the accounts that were created **active** and never verified (before sign-up made new accounts inactive until
/// the code is confirmed; an inactive account cannot sign in at all, as in zerobound).
///
/// A confirmed code stays confirmed, so a `true` is remembered for the life of the process and mercury is asked only until then.
/// mercury answering 404 (no code for the user) is "not verified". mercury unreachable, refusing or without an M2M client is not an
/// answer: the request is refused with a 503 (see `SessionValidationExceptionHandler`) rather than letting the user in or wrongly telling
/// a verified user to verify again.
@Component
public class VerificationStatus {

    private final MercuryClient mercuryClient;
    private final MercuryTokens mercuryTokens;
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();

    /// Builds the check.
    ///
    /// @param mercuryClient the client of mercury, which keeps the verification codes
    /// @param mercuryTokens SmartKin's M2M token towards mercury
    public VerificationStatus(MercuryClient mercuryClient, MercuryTokens mercuryTokens) {
        this.mercuryClient = mercuryClient;
        this.mercuryTokens = mercuryTokens;
    }

    /// Whether the user confirmed the e-mail code.
    ///
    /// @param userId the backbone user id (the `uid` of the session)
    /// @return `true` once mercury says the code was confirmed
    /// @throws SessionValidationUnavailableException when mercury cannot say
    public boolean isVerified(UUID userId) {
        if (verified.contains(userId)) {
            return true;
        }
        try {
            boolean done = Boolean.TRUE.equals(mercuryTokens.withToken(token -> mercuryClient.isVerificationCodeDone(token, userId)));
            if (done) {
                verified.add(userId);
            }
            return done;
        } catch (FeignException.NotFound e) {
            return false;
        } catch (FeignException | IllegalStateException e) {
            throw new SessionValidationUnavailableException("The verification status could not be read from mercury", e);
        }
    }
}
