package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.ResendVerificationRequest;
import com.umdc.smartkin.api.v1.to.VerificationStatusResponse;
import com.umdc.smartkin.api.v1.to.VerifyEmailRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupRequest;
import com.umdc.smartkin.client.backbone.to.BackboneEmailLookupResponse;
import com.umdc.smartkin.client.mercury.MailDeliveryException;
import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryMailer;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import com.umdc.smartkin.mapper.ConfirmCodeMapper;
import com.umdc.smartkin.security.verification.VerificationStatus;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import com.umdc.smartkin.util.EmailCooldown;
import com.umdc.smartkin.util.UpstreamFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.umdc.smartkin.util.JwtUtil;

import static com.umdc.smartkin.constant.SmartKinAppConstants.*;


///  Service implementation for user registration-related operations.
/// @see UserRegisterService
/// @see ConfirmCodeRequest
/// @see ResponseEntity
/// @see Service
@Service
public class UserRegisterServiceImpl implements UserRegisterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegisterServiceImpl.class);
    private static final String ATTEMPTS_REMAINING = "Attempts-Remaining";
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    static final String CODE_NOT_VALID_MESSAGE = "The code is not valid or has expired, ask for a new one.";
    private static final String ACTIVATION_FAILED = "The code is right but the account could not be activated, try again";

    @Value("${umdc.smartkin.application-id}")
    private UUID applicationId;

    private final MercuryClient mercuryClient;
    private final MercuryTokens mercuryTokens;
    private final BackboneClient backboneClient;
    private final ConfirmCodeMapper confirmCodeMapper;
    private final MercuryMailer mercuryMailer;
    private final EmailCooldown resendCooldown;
    private final VerificationStatus verificationStatus;

    /// Constructs a new UserRegisterServiceImpl.
    ///
    /// @param mercuryClient the client used to interact with the Mercury service
    /// @param mercuryTokens SmartKin's Mercury M2M token (from its Backbone managed client)
    /// @param backboneClient the client that activates the user once mercury accepts the code
    /// @param confirmCodeMapper the mapper used to map confirmation codes
    /// @param mercuryMailer hands the new code of a resend to mercury
    /// @param clock the clock the cooldown of a resend by e-mail is measured with
    /// @param verificationStatus whether a user confirmed the e-mail code (mercury, cached once confirmed)
    /// @see MercuryClient
    public UserRegisterServiceImpl(MercuryClient mercuryClient, MercuryTokens mercuryTokens, BackboneClient backboneClient,
                                   ConfirmCodeMapper confirmCodeMapper, MercuryMailer mercuryMailer, Clock clock,
                                   VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
        this.resendCooldown = new EmailCooldown(clock, RESEND_COOLDOWN);
        this.mercuryMailer = mercuryMailer;
        this.mercuryClient = mercuryClient;
        this.mercuryTokens = mercuryTokens;
        this.backboneClient = backboneClient;
        this.confirmCodeMapper = confirmCodeMapper;
    }

    /// Confirms the verification code with mercury and, when it is right, activates the user in backbone (backbone refuses the session of
    /// an inactive user). Mercury answers 202 for a right code, 406 for a wrong one (with `Attempts-Remaining`) and 404 when there is no
    /// code left to confirm; on a 404 the latest code is looked up, so an activation that failed after a right code is recovered by asking again.
    ///
    /// @param sessionToken the session token of the caller (backbone's)
    /// @param confirmCodeRequest the request object containing the user ID and verification code
    /// @return 200 when the user is active, otherwise mercury's status and headers (or 502 when backbone cannot activate)
    @Override
    public ResponseEntity<Void> confirmCode(String sessionToken, ConfirmCodeRequest confirmCodeRequest) {
        var userId = confirmCodeRequest.userId();
        var denied = refuseOthers(sessionToken, userId);
        if (denied != null) {
            return denied;
        }
        try {
            var verificationRequest = confirmCodeMapper.toVerificationCodeRequest(confirmCodeRequest, applicationId);
            mercuryTokens.withToken(mercuryToken -> mercuryClient.confirmCode(mercuryToken, verificationRequest));
            return activate(userId);
        } catch (FeignException.NotFound e) {
            if (alreadyVerified(userId)) {
                return activate(userId);
            }
            return relay(e);
        } catch (FeignException e) {
            return relay(e);
        }
    }

    /// Sends a new code to the e-mail of the caller. The e-mail and the name come from backbone, never from the request.
    ///
    /// @param sessionToken the session token of the caller, who must be the user
    /// @param userId the user who asks for a new code
    /// @return 202 when mercury took the message
    @Override
    public ResponseEntity<Void> resendCode(String sessionToken, UUID userId) {
        var denied = refuseOthers(sessionToken, userId);
        if (denied != null) {
            return denied;
        }
        if (alreadyVerified(userId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).header(MESSAGE_ERROR_HEADER, ALREADY_VERIFIED_MESSAGE).build();
        }
        try {
            var user = backboneClient.findUserById(userId);
            mercuryMailer.resendVerificationCode(userId, user.email(), user.displayName());
            return ResponseEntity.accepted().build();
        } catch (FeignException e) {
            LOGGER.warn("The user {} could not be read to resend the code (status {})", userId, e.status());
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).header(MESSAGE_ERROR_HEADER, USER_NOT_FOUND_MESSAGE).build();
            }
            return ResponseEntity.status(UpstreamFailure.statusToAnswer(e)).header(MESSAGE_ERROR_HEADER, VERIFICATION_UNAVAILABLE_MESSAGE).build();
        } catch (MailDeliveryException e) {
            LOGGER.error("Mercury did not take the new code of user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(MESSAGE_ERROR_HEADER, VERIFICATION_UNAVAILABLE_MESSAGE).build();
        }
    }

    /// Confirms the e-mail of a new account, which is inactive until now: mercury checks the code and, when it is right, backbone activates the
    /// user. The visitor has no session, so the account is named by its e-mail. An unknown e-mail and an account that is already active get the
    /// same `406` as a wrong code, so the endpoint does not list who is registered. A code that mercury accepted but whose activation failed is
    /// recovered on the next call (mercury then says "no code", but its latest one is verified).
    ///
    /// @param request the e-mail and the code
    /// @return 200 when the account is active, 406 when the code is not valid (`Attempts-Remaining` for a pending account), 502/503 when
    ///         backbone or mercury fails
    @Override
    public ResponseEntity<Void> verifyEmail(VerifyEmailRequest request) {
        Optional<BackboneEmailLookupResponse> pending;
        try {
            pending = lookup(normalize(request.email())).filter(owner -> !Boolean.TRUE.equals(owner.active()));
        } catch (FeignException e) {
            LOGGER.warn("The owner of an e-mail could not be looked up to verify it (status {})", e.status());
            return unavailable(e);
        }
        if (pending.isEmpty()) {
            return codeNotValid(null);
        }
        var userId = pending.get().id();
        try {
            var verificationRequest = confirmCodeMapper.toVerificationCodeRequest(new ConfirmCodeRequest(userId, request.verificationCode()), applicationId);
            mercuryTokens.withToken(mercuryToken -> mercuryClient.confirmCode(mercuryToken, verificationRequest));
            return activate(userId);
        } catch (FeignException.NotFound _) {
            return alreadyVerified(userId) ? activate(userId) : codeNotValid("0");
        } catch (FeignException e) {
            if (e.status() == HttpStatus.NOT_ACCEPTABLE.value()) {
                return codeNotValid(Optional.ofNullable(e.responseHeaders().get(ATTEMPTS_REMAINING)).flatMap(values -> values.stream().findFirst()).orElse("0"));
            }
            LOGGER.error("Mercury could not check the verification code of user {} (status {})", userId, e.status());
            return unavailable(e);
        }
    }

    /// Sends a new code to the e-mail of an account that is not verified yet. One code per e-mail per minute; the answer is always 202.
    ///
    /// @param request the e-mail
    /// @return always 202
    @Override
    public ResponseEntity<Void> resendVerification(ResendVerificationRequest request) {
        var email = normalize(request.email());
        if (!resendCooldown.takeTurn(email)) {
            LOGGER.info("A verification code was asked again within the cooldown");
            return ResponseEntity.accepted().build();
        }
        try {
            lookup(email).filter(owner -> !Boolean.TRUE.equals(owner.active())).ifPresentOrElse(
                    owner -> mercuryMailer.resendVerificationCode(owner.id(), owner.email(), owner.displayName()),
                    () -> LOGGER.info("A verification code was asked for an e-mail without a pending account"));
        } catch (FeignException e) {
            LOGGER.warn("The owner of an e-mail could not be looked up to resend the code (status {})", e.status());
        } catch (MailDeliveryException e) {
            LOGGER.error("Mercury did not take the new verification code: {}", e.getMessage());
        }
        return ResponseEntity.accepted().build();
    }

    private Optional<BackboneEmailLookupResponse> lookup(String email) {
        try {
            return Optional.ofNullable(backboneClient.lookupByEmail(applicationId, new BackboneEmailLookupRequest(email)));
        } catch (FeignException.NotFound _) {
            return Optional.empty();
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /// Whether the caller confirmed the e-mail code. The token was validated by the session interceptor before this runs.
    ///
    /// @param sessionToken the session token of the caller (backbone's)
    /// @return 200 with the status, 403 when the token names no user; a 503 when mercury cannot say (`SessionValidationExceptionHandler`)
    @Override
    public ResponseEntity<VerificationStatusResponse> status(String sessionToken) {
        var callerId = JwtUtil.getUidFromToken(sessionToken);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header(MESSAGE_ERROR_HEADER, CALLER_UNKNOWN_MESSAGE).build();
        }
        return ResponseEntity.ok(new VerificationStatusResponse(verificationStatus.isVerified(callerId)));
    }

    private static ResponseEntity<Void> codeNotValid(String attemptsRemaining) {
        var response = ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).header(MESSAGE_ERROR_HEADER, CODE_NOT_VALID_MESSAGE);
        if (attemptsRemaining != null) {
            response.header(ATTEMPTS_REMAINING, attemptsRemaining);
        }
        return response.build();
    }

    private static ResponseEntity<Void> unavailable(FeignException e) {
        return ResponseEntity.status(UpstreamFailure.statusToAnswer(e)).header(MESSAGE_ERROR_HEADER, VERIFICATION_UNAVAILABLE_MESSAGE).build();
    }

    private static ResponseEntity<Void> refuseOthers(String sessionToken, UUID userId) {
        var callerId = JwtUtil.getUidFromToken(sessionToken);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header(MESSAGE_ERROR_HEADER, CALLER_UNKNOWN_MESSAGE).build();
        }
        if (!callerId.equals(userId)) {
            LOGGER.warn("User {} tried to act on the verification of {}", callerId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header(MESSAGE_ERROR_HEADER, OWN_ACCOUNT_ONLY_MESSAGE).build();
        }
        return null;
    }

    private boolean alreadyVerified(UUID userId) {
        try {
            return Boolean.TRUE.equals(mercuryTokens.withToken(mercuryToken -> mercuryClient.isVerificationCodeDone(mercuryToken, userId)));
        } catch (FeignException e) {
            LOGGER.warn("The latest verification status of user {} could not be read (status {})", userId, e.status());
            return false;
        }
    }

    private ResponseEntity<Void> activate(UUID userId) {
        try {
            backboneClient.activateUser(applicationId, userId);
            LOGGER.info("User {} verified and activated", userId);
            return ResponseEntity.ok().build();
        } catch (FeignException e) {
            LOGGER.error("Backbone could not activate user {} (status {}): the code is spent, asking again recovers it", userId, e.status());
            return ResponseEntity.status(UpstreamFailure.statusToAnswer(e)).header(MESSAGE_HEADER, ACTIVATION_FAILED).build();
        }
    }

    private static ResponseEntity<Void> relay(FeignException e) {
        Optional<String> message = Optional.ofNullable(e.responseHeaders().get(MESSAGE_HEADER))
                .flatMap(values -> values.stream().findFirst());
        var response = ResponseEntity.status(e.status()).header(MESSAGE_HEADER, message.orElse(""));
        Optional.ofNullable(e.responseHeaders().get(ATTEMPTS_REMAINING)).flatMap(values -> values.stream().findFirst())
                .ifPresent(attempts -> response.header(ATTEMPTS_REMAINING, attempts));
        return response.build();
    }
}
