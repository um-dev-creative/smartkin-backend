package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.ForgotPasswordRequest;
import com.umdc.smartkin.api.v1.to.ResetPasswordRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupRequest;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupResponse;
import com.umdc.smartkin.client.backbone.to.BackbonePasswordResetRequest;
import com.umdc.smartkin.client.mercury.MailDeliveryException;
import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryMailer;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import com.umdc.smartkin.mapper.ConfirmCodeMapper;
import com.umdc.smartkin.util.UpstreamFailure;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.VERIFICATION_UNAVAILABLE_MESSAGE;

/// Password recovery. Backbone proves nothing about the visitor, so SmartKin does: it e-mails a single-use code through mercury (which keeps it,
/// counts the attempts and expires it) and only a right code reaches backbone's password reset.
///
/// The visitor is anonymous, so nothing may tell an account that exists from one that does not: `forgot` always answers 202 and `reset` answers
/// the same 406 for an unknown account, an inactive one, a wrong code, a spent code and an expired one. One reset code per e-mail per minute
/// keeps the endpoint from being used to flood an inbox. Unlike the confirmation of a new account, a code that mercury no longer has is never
/// "recovered" from its latest status: a reset needs a fresh, right code every time.
@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);
    static final Duration COOLDOWN = Duration.ofMinutes(1);
    static final String CODE_NOT_VALID_MESSAGE = "The code is not valid or has expired, ask for a new one.";
    static final String PASSWORD_REJECTED_MESSAGE = "The password does not meet the security policy; ask for a new code and choose another one.";
    private static final int MAX_TRACKED_EMAILS = 10_000;

    @Value("${umdc.smartkin.application-id}")
    private UUID applicationId;

    private final BackboneClient backboneClient;
    private final MercuryClient mercuryClient;
    private final MercuryTokens mercuryTokens;
    private final MercuryMailer mercuryMailer;
    private final ConfirmCodeMapper confirmCodeMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> lastRequest = new ConcurrentHashMap<>();

    /// Builds the service.
    ///
    /// @param backboneClient    finds the owner of the e-mail and sets the password
    /// @param mercuryClient     confirms the code
    /// @param mercuryTokens     SmartKin's Mercury M2M token
    /// @param mercuryMailer     hands the e-mail to mercury
    /// @param confirmCodeMapper builds mercury's confirmation request
    /// @param clock             the clock the cooldown is measured with
    public PasswordRecoveryServiceImpl(BackboneClient backboneClient, MercuryClient mercuryClient, MercuryTokens mercuryTokens,
                                       MercuryMailer mercuryMailer, ConfirmCodeMapper confirmCodeMapper, Clock clock) {
        this.backboneClient = backboneClient;
        this.mercuryClient = mercuryClient;
        this.mercuryTokens = mercuryTokens;
        this.mercuryMailer = mercuryMailer;
        this.confirmCodeMapper = confirmCodeMapper;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<Void> forgot(ForgotPasswordRequest request) {
        var email = normalize(request.email());
        if (!takeTurn(email)) {
            LOGGER.info("A password reset code was asked again within the cooldown");
            return ResponseEntity.accepted().build();
        }
        try {
            lookup(email).filter(owner -> Boolean.TRUE.equals(owner.active())).ifPresentOrElse(
                    owner -> mercuryMailer.sendPasswordResetCode(owner.id(), owner.email(), owner.displayName()),
                    () -> LOGGER.info("A password reset code was asked for an e-mail without an active account"));
        } catch (FeignException e) {
            LOGGER.warn("The owner of an e-mail could not be looked up for a password reset (status {})", e.status());
        } catch (MailDeliveryException e) {
            LOGGER.error("The password reset e-mail was not handed to mercury: {}", e.getMessage());
        }
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> reset(ResetPasswordRequest request) {
        Optional<BackboneEmailLookupResponse> owner;
        try {
            owner = lookup(normalize(request.email())).filter(found -> Boolean.TRUE.equals(found.active()));
        } catch (FeignException e) {
            LOGGER.warn("The owner of an e-mail could not be looked up for a password reset (status {})", e.status());
            return unavailable(e);
        }
        if (owner.isEmpty()) {
            return notValid();
        }
        var userId = owner.get().id();
        var verification = confirmCodeMapper.toVerificationCodeRequest(new ConfirmCodeRequest(userId, request.code()), applicationId);
        try {
            mercuryTokens.withToken(mercuryToken -> mercuryClient.confirmCode(mercuryToken, verification));
        } catch (FeignException e) {
            if (e.status() == HttpStatus.NOT_ACCEPTABLE.value() || e.status() == HttpStatus.NOT_FOUND.value()) {
                LOGGER.warn("A password reset code of user {} was refused by mercury (status {})", userId, e.status());
                return notValid();
            }
            LOGGER.error("Mercury could not check the password reset code of user {} (status {})", userId, e.status());
            return unavailable(e);
        }
        return setPassword(userId, request.newPassword());
    }

    private ResponseEntity<Void> setPassword(UUID userId, String newPassword) {
        try {
            backboneClient.resetPassword(applicationId, userId, new BackbonePasswordResetRequest(newPassword));
            LOGGER.info("Password of user {} reset", userId);
            return ResponseEntity.noContent().build();
        } catch (FeignException e) {
            if (e.status() == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
                var violations = Optional.ofNullable(e.responseHeaders().get(HttpHeaders.WARNING)).flatMap(values -> values.stream().findFirst())
                        .orElse(PASSWORD_REJECTED_MESSAGE);
                LOGGER.warn("Backbone refused the new password of user {} by its policy", userId);
                return ResponseEntity.unprocessableContent().header(MESSAGE_ERROR_HEADER, violations).build();
            }
            LOGGER.error("Backbone could not set the new password of user {} (status {}): the code is spent, ask for a new one", userId, e.status());
            return unavailable(e);
        }
    }

    private Optional<BackboneEmailLookupResponse> lookup(String email) {
        try {
            return Optional.ofNullable(backboneClient.lookupByEmail(applicationId, new BackboneEmailLookupRequest(email)));
        } catch (FeignException.NotFound _) {
            return Optional.empty();
        }
    }

    /// One code per e-mail per cooldown. The table is bounded: when it is full, the entries whose cooldown is over are dropped.
    private boolean takeTurn(String email) {
        var now = clock.instant();
        if (lastRequest.size() >= MAX_TRACKED_EMAILS) {
            lastRequest.values().removeIf(at -> at.plus(COOLDOWN).isBefore(now));
        }
        var taken = new boolean[1];
        lastRequest.compute(email, (key, previous) -> {
            taken[0] = previous == null || !previous.plus(COOLDOWN).isAfter(now);
            return taken[0] ? now : previous;
        });
        return taken[0];
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseEntity<Void> notValid() {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).header(MESSAGE_ERROR_HEADER, CODE_NOT_VALID_MESSAGE).build();
    }

    private static ResponseEntity<Void> unavailable(FeignException e) {
        return ResponseEntity.status(UpstreamFailure.statusToAnswer(e)).header(MESSAGE_ERROR_HEADER, VERIFICATION_UNAVAILABLE_MESSAGE).build();
    }
}
