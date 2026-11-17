package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.PasswordRecoveryService;
import com.umdc.smartkin.api.v1.to.ForgotPasswordRequest;
import com.umdc.smartkin.api.v1.to.ResetPasswordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryControllerTest {

    @Test
    void bothOperationsGoThroughTheServiceAndAnswerWhatItAnswers() {
        var service = mock(PasswordRecoveryService.class);
        var forgot = new ForgotPasswordRequest("ana@smartkin.test");
        var reset = new ResetPasswordRequest("ana@smartkin.test", "1234-5678", "N3w$Passw0rd");
        when(service.forgot(forgot)).thenReturn(ResponseEntity.accepted().build());
        when(service.reset(reset)).thenReturn(ResponseEntity.noContent().build());
        var controller = new PasswordRecoveryController(service);

        assertEquals(HttpStatus.ACCEPTED, controller.forgot(forgot).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.reset(reset).getStatusCode());
        verify(service).forgot(forgot);
        verify(service).reset(reset);
    }

    @Test
    void theInterfaceDefaultsAreNotImplemented() {
        var empty = new PasswordRecoveryApi() {};

        assertThrows(UnsupportedOperationException.class, () -> empty.forgot(new ForgotPasswordRequest("a@b.co")));
        assertThrows(UnsupportedOperationException.class, () -> empty.reset(new ResetPasswordRequest("a@b.co", "1234-5678", "12345678")));
    }
}
