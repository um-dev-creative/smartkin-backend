package com.umdc.smartkin.api.v1.controller;

import com.umdc.security.annotation.SkipSessionValidation;
import com.umdc.smartkin.api.v1.service.PasswordRecoveryService;
import com.umdc.smartkin.api.v1.to.ForgotPasswordRequest;
import com.umdc.smartkin.api.v1.to.ResetPasswordRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/password-recovery")
public class PasswordRecoveryController implements PasswordRecoveryApi {

    private final PasswordRecoveryService service;

    /// @param service the password recovery service
    public PasswordRecoveryController(PasswordRecoveryService service) {
        this.service = service;
    }

    @Override
    public PasswordRecoveryService getService() {
        return service;
    }

    @Override
    @SkipSessionValidation("The visitor cannot sign in, so there is no session; the answer never reveals whether the e-mail is registered and a code is sent at most once a minute")
    public ResponseEntity<Void> forgot(ForgotPasswordRequest request) {
        return PasswordRecoveryApi.super.forgot(request);
    }

    @Override
    @SkipSessionValidation("The visitor cannot sign in, so there is no session; a single-use code from the owner's e-mail (checked by mercury) is what authorizes the change")
    public ResponseEntity<Void> reset(ResetPasswordRequest request) {
        return PasswordRecoveryApi.super.reset(request);
    }
}
