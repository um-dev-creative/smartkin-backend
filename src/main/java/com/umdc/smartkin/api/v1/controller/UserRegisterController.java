package com.umdc.smartkin.api.v1.controller;

import com.umdc.security.annotation.SkipSessionValidation;
import com.umdc.smartkin.api.v1.service.UserRegisterService;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.VerificationStatusResponse;
import com.umdc.smartkin.api.v1.to.ResendVerificationRequest;
import com.umdc.smartkin.api.v1.to.VerifyEmailRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-register")
public class UserRegisterController implements UserRegisterApi {

    private final UserRegisterService userRegisterService;

    public UserRegisterController(UserRegisterService userRegisterService) {
        this.userRegisterService = userRegisterService;
    }

    @Override
    public ResponseEntity<Void> confirmCode(String sessionToken, ConfirmCodeRequest confirmCodeRequest) {
        return userRegisterService.confirmCode(sessionToken, confirmCodeRequest);
    }

    @Override
    public ResponseEntity<VerificationStatusResponse> status(String sessionToken) {
        return userRegisterService.status(sessionToken);
    }

    @Override
    public ResponseEntity<Void> resendCode(String sessionToken, java.util.UUID userId) {
        return userRegisterService.resendCode(sessionToken, userId);
    }

    @Override
    @SkipSessionValidation("A new account is inactive and cannot sign in, so there is no session; the e-mailed single-use code, checked by mercury, is what authorizes the activation")
    public ResponseEntity<Void> verifyEmail(VerifyEmailRequest request) {
        return userRegisterService.verifyEmail(request);
    }

    @Override
    @SkipSessionValidation("A new account is inactive and cannot sign in, so there is no session; the answer never reveals whether the e-mail is registered and a code is sent at most once a minute")
    public ResponseEntity<Void> resendVerification(ResendVerificationRequest request) {
        return userRegisterService.resendVerification(request);
    }
}
