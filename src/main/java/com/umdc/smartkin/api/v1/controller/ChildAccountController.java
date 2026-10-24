package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildAccountService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// REST controller for the accounts a parent creates for their children.
@RestController
@RequestMapping("/api/v1/children/accounts")
public class ChildAccountController implements ChildAccountApi {

    private final ChildAccountService accountService;

    /// Creates the controller.
    ///
    /// @param accountService the service that backs the operations
    public ChildAccountController(ChildAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public ChildAccountService getService() {
        return accountService;
    }
}
