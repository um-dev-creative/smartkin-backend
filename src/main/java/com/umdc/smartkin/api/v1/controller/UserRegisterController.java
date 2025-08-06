package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.UserRegisterService;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
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
    public ResponseEntity<Void> confirmCode(String sessionTokenBkd, ConfirmCodeRequest confirmCodeRequest) {
        return userRegisterService.confirmCode(sessionTokenBkd, confirmCodeRequest);
    }
}
