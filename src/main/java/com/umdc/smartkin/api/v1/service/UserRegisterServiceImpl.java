package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.MercuryTokens;
import com.umdc.smartkin.mapper.ConfirmCodeMapper;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import com.umdc.smartkin.util.UpstreamFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_HEADER;


///  Service implementation for user registration-related operations.
/// @see UserRegisterService
/// @see ConfirmCodeRequest
/// @see ResponseEntity
/// @see Service
@Service
public class UserRegisterServiceImpl implements UserRegisterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegisterServiceImpl.class);
    private static final String ATTEMPTS_REMAINING = "Attempts-Remaining";
    private static final String ACTIVATION_FAILED = "The code is right but the account could not be activated, try again";

    @Value("${umdc.smartkin.application-id}")
    private UUID applicationId;

    private final MercuryClient mercuryClient;
    private final MercuryTokens mercuryTokens;
    private final BackboneClient backboneClient;
    private final ConfirmCodeMapper confirmCodeMapper;

    /// Constructs a new UserRegisterServiceImpl.
    ///
    /// @param mercuryClient the client used to interact with the Mercury service
    /// @param mercuryTokens SmartKin's Mercury M2M token (from its Backbone managed client)
    /// @param backboneClient the client that activates the user once mercury accepts the code
    /// @param confirmCodeMapper the mapper used to map confirmation codes
    /// @see MercuryClient
    public UserRegisterServiceImpl(MercuryClient mercuryClient, MercuryTokens mercuryTokens, BackboneClient backboneClient,
                                   ConfirmCodeMapper confirmCodeMapper) {
        this.mercuryClient = mercuryClient;
        this.mercuryTokens = mercuryTokens;
        this.backboneClient = backboneClient;
        this.confirmCodeMapper = confirmCodeMapper;
    }

    /// Confirms the verification code with mercury and, when it is right, activates the user in backbone (backbone refuses the session of
    /// an inactive user). Mercury answers 202 for a right code, 406 for a wrong one (with `Attempts-Remaining`) and 404 when there is no
    /// code left to confirm; on a 404 the latest code is looked up, so an activation that failed after a right code is recovered by asking again.
    ///
    /// @param sessionTokenBkd the backbone token session
    /// @param confirmCodeRequest the request object containing the user ID and verification code
    /// @return 200 when the user is active, otherwise mercury's status and headers (or 502 when backbone cannot activate)
    @Override
    public ResponseEntity<Void> confirmCode(String sessionTokenBkd, ConfirmCodeRequest confirmCodeRequest) {
        var userId = confirmCodeRequest.userId();
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
