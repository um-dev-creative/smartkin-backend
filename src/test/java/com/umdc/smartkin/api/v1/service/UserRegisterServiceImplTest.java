package com.umdc.smartkin.api.v1.service;

import com.umdc.security.to.AuthRequest;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.PrxTokenString;
import com.umdc.smartkin.client.mercury.MercuryClient;
import com.umdc.smartkin.client.mercury.to.VerificationCodeRequest;
import com.umdc.smartkin.mapper.ConfirmCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.backboneUser;
import static com.umdc.smartkin.support.TestData.feign;
import static com.umdc.smartkin.support.TestData.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisterServiceImplTest {

    private static final String SESSION_BKD = "bkd-token";
    private static final String MERCURY_TOKEN = "mercury-token";

    @Mock
    private MercuryClient mercuryClient;
    @Mock
    private BackboneClient backboneClient;
    @Mock
    private ConfirmCodeMapper confirmCodeMapper;

    private UserRegisterServiceImpl service;
    private final ConfirmCodeRequest request = new ConfirmCodeRequest(USER_ID, "1234-5678");

    @BeforeEach
    void setUp() {
        service = new UserRegisterServiceImpl(mercuryClient, confirmCodeMapper, backboneClient);
        ReflectionTestUtils.setField(service, "applicationId", APPLICATION_ID);
        when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
    }

    @Test
    void confirmsTheCodeWithTheMercuryToken() {
        var verificationRequest = mock(VerificationCodeRequest.class);
        when(mercuryClient.token(any(), any(AuthRequest.class))).thenReturn(new PrxTokenString(MERCURY_TOKEN));
        when(confirmCodeMapper.toVerificationCodeRequest(request, APPLICATION_ID)).thenReturn(verificationRequest);
        when(mercuryClient.confirmCode(MERCURY_TOKEN, verificationRequest)).thenReturn(ResponseEntity.noContent().build());

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(mercuryClient).token(SESSION_BKD, new AuthRequest("alopez", "pwd"));
    }

    @Test
    void relaysTheMercuryStatusAndMessageHeader() {
        when(mercuryClient.token(any(), any(AuthRequest.class)))
                .thenThrow(feign(422, Map.of("message", List.of("Invalid code"))));

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("Invalid code", response.getHeaders().getFirst("message"));
    }

    @Test
    void relaysTheStatusWithAnEmptyMessageWhenMercuryDoesNotSendOne() {
        when(mercuryClient.token(any(), any(AuthRequest.class))).thenThrow(feign(500));

        var response = service.confirmCode(SESSION_BKD, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("", response.getHeaders().getFirst("message"));
    }
}
