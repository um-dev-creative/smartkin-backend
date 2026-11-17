package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.GetUserResponse;
import com.umdc.smartkin.api.v1.to.PutUserRequest;
import com.umdc.smartkin.api.v1.to.UserCreateResponse;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.BackboneProfileImageRefResponse;
import com.umdc.smartkin.client.backbone.to.BackboneUserCreateRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserCreateResponse;
import com.umdc.smartkin.client.backbone.to.BackboneUserUpdateRequest;
import com.umdc.smartkin.client.mercury.MailDeliveryException;
import com.umdc.smartkin.client.mercury.MercuryMailer;
import com.umdc.smartkin.mapper.GetUserMapper;
import com.umdc.smartkin.mapper.PutUserMapper;
import com.umdc.smartkin.mapper.UserCreateMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.ROLE_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.backboneUser;
import static com.umdc.smartkin.support.TestData.feign;
import static com.umdc.smartkin.support.TestData.person;
import static com.umdc.smartkin.support.TestData.userCreateRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String TOKEN = com.umdc.smartkin.support.TestData.tokenFor(USER_ID);
    private static final UUID OTHER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final String MESSAGE_ERROR = "message-error";
    private static final String MESSAGE = "message";

    @Mock
    private BackboneClient backboneClient;
    @Mock
    private MercuryMailer mailer;
    @Mock
    private UserCreateMapper userCreateMapper;
    @Mock
    private PutUserMapper putUserMapper;
    @Mock
    private GetUserMapper getUserMapper;
    @Mock
    private AccessPolicy accessPolicy;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(backboneClient, mailer, userCreateMapper, putUserMapper, getUserMapper, accessPolicy);
        lenient().when(accessPolicy.canViewUser(USER_ID, USER_ID)).thenReturn(true);
        lenient().when(accessPolicy.isSelf(USER_ID, USER_ID)).thenReturn(true);
        ReflectionTestUtils.setField(service, "applicationIdString", APPLICATION_ID.toString());
        ReflectionTestUtils.setField(service, "initialRoleId", ROLE_ID.toString());
    }

    private static UserCreateResponse createResponse() {
        return new UserCreateResponse(USER_ID, "alopez", "ana@smartkin.test", LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0), true, UUID.randomUUID(), ROLE_ID, APPLICATION_ID, "Ana", true, true, false);
    }

    private static PutUserRequest putRequest(List<UUID> roleIds) {
        return new PutUserRequest("Ana", "Lopez", "Ana L", true, false, false, UUID.randomUUID(), "+15550100", roleIds, true);
    }

    @Nested
    class Create {

        private void stubCreateFlow() {
            when(userCreateMapper.toBackbone(any(), eq(APPLICATION_ID), eq(ROLE_ID), any()))
                    .thenReturn(mock(BackboneUserCreateRequest.class));
            when(backboneClient.post(any())).thenReturn(mock(BackboneUserCreateResponse.class));
            when(userCreateMapper.fromBackbone(any())).thenReturn(createResponse());
        }

        @Test
        void createsTheUserAndSendsTheVerificationEmail() {
            stubCreateFlow();

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            verify(mailer).sendVerificationCode(USER_ID, "ana@smartkin.test", "Ana Lopez");
        }

        @Test
        void usesShortAliasWhenTheLastnameIsShort() {
            stubCreateFlow();

            service.create(userCreateRequest("Lo", "+15550100"));

            var alias = ArgumentCaptor.forClass(String.class);
            verify(userCreateMapper).toBackbone(any(), any(), any(), alias.capture());
            assertTrue(alias.getValue().startsWith("alo"));
        }

        @Test
        void truncatesLongAliases() {
            stubCreateFlow();

            service.create(userCreateRequest("Wolfeschlegelsteinhausen", "+15550100"));

            var alias = ArgumentCaptor.forClass(String.class);
            verify(userCreateMapper).toBackbone(any(), any(), any(), alias.capture());
            assertTrue(alias.getValue().length() <= 11);
        }

        @Test
        void rejectsANullRequest() {
            var response = service.create(null);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Content null invalid", response.getHeaders().getFirst(MESSAGE_ERROR));
            verifyNoInteractions(backboneClient, mailer);
        }

        @Test
        void answersConflictWhenTheEmailAlreadyExists() {
            when(backboneClient.checkEmail(any(), eq(APPLICATION_ID))).thenThrow(feign(409));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals("Email already exists", response.getHeaders().getFirst(MESSAGE_ERROR));
            verify(mailer, never()).sendVerificationCode(any(), any(), any());
        }

        @Test
        void removesTheUserAndAnswers503WhenMercuryDoesNotTakeTheVerificationEmail() {
            stubCreateFlow();
            doThrow(new MailDeliveryException("Send failed")).when(mailer).sendVerificationCode(any(), any(), any());

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertEquals("Verification e-mail could not be sent", response.getHeaders().getFirst(MESSAGE_ERROR));
            verify(backboneClient).deleteUserByUserIdAndApplicationId(eq(APPLICATION_ID), any());
        }

        @Test
        void stillAnswers503WhenTheUserCannotBeRemovedEither() {
            stubCreateFlow();
            doThrow(new MailDeliveryException("Send failed")).when(mailer).sendVerificationCode(any(), any(), any());
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(500));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        }

        @Test
        void doesNotReportAnExistingEmailWhenBackboneRefusesTheServiceCredential() {
            when(backboneClient.checkEmail(any(), eq(APPLICATION_ID))).thenThrow(feign(403));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
            assertEquals("Identity service error", response.getHeaders().getFirst(MESSAGE_ERROR));
            verify(mailer, never()).sendVerificationCode(any(), any(), any());
        }

        @Test
        void answersBadGatewayWhenBackboneCannotBeReached() {
            when(backboneClient.checkEmail(any(), eq(APPLICATION_ID))).thenThrow(feign(-1));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        }

        @Test
        void relaysTheStatusBackboneAnswered() {
            when(backboneClient.checkEmail(any(), eq(APPLICATION_ID))).thenThrow(feign(400));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Identity service error", response.getHeaders().getFirst(MESSAGE_ERROR));
        }

        @Test
        void answersConflictWhenNoFreeAliasIsFound() {
            when(backboneClient.checkAlias(any(), eq(APPLICATION_ID))).thenThrow(feign(409));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals("Error creating user", response.getHeaders().getFirst(MESSAGE_ERROR));
            verify(backboneClient, times(6)).checkAlias(any(), any());
        }
    }

    @Nested
    class FindUser {

        private void stubMapper() {
            when(getUserMapper.fromBackbone(any(), any())).thenAnswer(invocation -> mock(GetUserResponse.class));
        }

        @Test
        void returnsTheUserWithItsProfileImageReference() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
            when(backboneClient.getProfileImageRef(TOKEN, APPLICATION_ID))
                    .thenReturn(ResponseEntity.ok(new BackboneProfileImageRefResponse("images/ana.png")));
            stubMapper();

            var response = service.findUser(TOKEN, USER_ID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(getUserMapper).fromBackbone(any(), eq("images/ana.png"));
        }

        @Test
        void usesAnEmptyReferenceWhenTheResponseHasNoBody() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
            when(backboneClient.getProfileImageRef(TOKEN, APPLICATION_ID)).thenReturn(ResponseEntity.ok().build());
            stubMapper();

            service.findUser(TOKEN, USER_ID);

            verify(getUserMapper).fromBackbone(any(), eq(""));
        }

        @Test
        void usesAnEmptyReferenceWhenTheBodyHasNoRef() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
            when(backboneClient.getProfileImageRef(TOKEN, APPLICATION_ID))
                    .thenReturn(ResponseEntity.ok(new BackboneProfileImageRefResponse(null)));
            stubMapper();

            service.findUser(TOKEN, USER_ID);

            verify(getUserMapper).fromBackbone(any(), eq(""));
        }

        @Test
        void usesAnEmptyReferenceWhenTheImageIsNotFound() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
            when(backboneClient.getProfileImageRef(TOKEN, APPLICATION_ID)).thenThrow(feign(404));
            stubMapper();

            assertEquals(HttpStatus.OK, service.findUser(TOKEN, USER_ID).getStatusCode());
            verify(getUserMapper).fromBackbone(any(), eq(""));
        }

        @Test
        void usesAnEmptyReferenceWhenTheImageLookupFails() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
            when(backboneClient.getProfileImageRef(TOKEN, APPLICATION_ID)).thenThrow(feign(500));
            stubMapper();

            assertEquals(HttpStatus.OK, service.findUser(TOKEN, USER_ID).getStatusCode());
            verify(getUserMapper).fromBackbone(any(), eq(""));
        }

        @Test
        void answersNotFoundWhenTheUserDoesNotExist() {
            when(backboneClient.findUserById(USER_ID)).thenThrow(feign(404));

            var response = service.findUser(TOKEN, USER_ID);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("User not found.", response.getHeaders().getFirst(MESSAGE_ERROR));
        }

        @Test
        void relaysOtherBackboneStatuses() {
            when(backboneClient.findUserById(USER_ID)).thenThrow(feign(503));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, service.findUser(TOKEN, USER_ID).getStatusCode());
        }
    }

    @Nested
    class Update {

        private void stubExistingUser() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(backboneUser(person("Phone"), List.of(), List.of()));
        }

        private void stubUpdate(ResponseEntity<Void> backboneResponse) {
            when(putUserMapper.toBackbone(eq(APPLICATION_ID), any())).thenReturn(mock(BackboneUserUpdateRequest.class));
            when(backboneClient.userPartialUpdate(eq(USER_ID), any())).thenReturn(backboneResponse);
        }

        @Test
        void updatesTheUserAndRelaysTheBackboneResponse() {
            stubExistingUser();
            stubUpdate(ResponseEntity.noContent().build());

            var response = service.update(TOKEN, USER_ID, putRequest(null));

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        }

        @Test
        void updatesWithoutRoleChanges() {
            stubExistingUser();
            stubUpdate(ResponseEntity.ok().build());

            assertEquals(HttpStatus.OK, service.update(TOKEN, USER_ID, putRequest(null)).getStatusCode());
        }

        @Test
        void rejectsANullRequest() {
            var response = service.update(TOKEN, USER_ID, null);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Request body is missing.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void rejectsANullUserId() {
            var response = service.update(TOKEN, null, putRequest(null));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("User ID is required.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void refusesAnyRoleChangeBecauseAUserMustNotChooseTheirOwnRoles() {
            var response = service.update(TOKEN, USER_ID, putRequest(List.of(ROLE_ID)));

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("Roles cannot be changed through this operation.", response.getHeaders().getFirst(MESSAGE_ERROR));
            verifyNoInteractions(backboneClient);
        }

        @Test
        void refusesToUpdateAnotherUsersAccount() {
            var response = service.update(TOKEN, OTHER_ID, putRequest(null));

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verifyNoInteractions(backboneClient);
        }

        @Test
        void refusesATokenThatDoesNotIdentifyAUser() {
            assertEquals(HttpStatus.FORBIDDEN, service.update("garbage", USER_ID, putRequest(null)).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, service.deleteUserByUserAndApplication("garbage", USER_ID).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, service.findUser("garbage", USER_ID).getStatusCode());
            verifyNoInteractions(backboneClient);
        }

        @Test
        void refusesToDeleteOrReadAnotherUsersAccount() {
            assertEquals(HttpStatus.FORBIDDEN, service.deleteUserByUserAndApplication(TOKEN, OTHER_ID).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, service.findUser(TOKEN, OTHER_ID).getStatusCode());
            verifyNoInteractions(backboneClient);
        }

        @Test
        void answersNotFoundWhenBackboneReturnsNoUser() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(null);

            var response = service.update(TOKEN, USER_ID, putRequest(null));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("User not found.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void answersNotFoundWhenTheLookupReturns404() {
            when(backboneClient.findUserById(USER_ID)).thenThrow(feign(404));

            assertEquals(HttpStatus.NOT_FOUND, service.update(TOKEN, USER_ID, putRequest(null)).getStatusCode());
        }

        @Test
        void answersServerErrorWhenTheLookupFails() {
            when(backboneClient.findUserById(USER_ID)).thenThrow(feign(502));

            var response = service.update(TOKEN, USER_ID, putRequest(null));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("Error updating user.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void answersNotFoundWhenTheUpdateReturns404() {
            stubExistingUser();
            when(putUserMapper.toBackbone(any(), any())).thenReturn(mock(BackboneUserUpdateRequest.class));
            when(backboneClient.userPartialUpdate(eq(USER_ID), any())).thenThrow(feign(404));

            assertEquals(HttpStatus.NOT_FOUND, service.update(TOKEN, USER_ID, putRequest(null)).getStatusCode());
        }

        @Test
        void answersBadRequestWhenTheUpdateReturns400() {
            stubExistingUser();
            when(putUserMapper.toBackbone(any(), any())).thenReturn(mock(BackboneUserUpdateRequest.class));
            when(backboneClient.userPartialUpdate(eq(USER_ID), any())).thenThrow(feign(400));

            var response = service.update(TOKEN, USER_ID, putRequest(null));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid request data.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void answersServerErrorOnUnexpectedFailures() {
            stubExistingUser();
            when(putUserMapper.toBackbone(any(), any())).thenThrow(new IllegalStateException("boom"));

            var response = service.update(TOKEN, USER_ID, putRequest(null));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("An unexpected error occurred while updating the user.", response.getHeaders().getFirst(MESSAGE));
        }
    }

    @Nested
    class Delete {

        @Test
        void returnsNoContentWhenTheUserIsDeleted() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(APPLICATION_ID, USER_ID))
                    .thenReturn(ResponseEntity.noContent().build());

            assertEquals(HttpStatus.NO_CONTENT, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }

        @Test
        void returnsNotModifiedForAnyOtherSuccessStatus() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(APPLICATION_ID, USER_ID))
                    .thenReturn(ResponseEntity.ok().build());

            assertEquals(HttpStatus.NOT_MODIFIED, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }

        @Test
        void relaysNotFound() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(404));

            assertEquals(HttpStatus.NOT_FOUND, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }

        @Test
        void relaysForbidden() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(403));

            assertEquals(HttpStatus.FORBIDDEN, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }

        @Test
        void relaysBadRequest() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(400));

            assertEquals(HttpStatus.BAD_REQUEST, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }

        @Test
        void answersServerErrorForOtherBackboneStatuses() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(502));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }

        @Test
        void answersServerErrorOnUnexpectedFailures() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(new IllegalStateException("boom"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, service.deleteUserByUserAndApplication(TOKEN, USER_ID).getStatusCode());
        }
    }

    @Test
    void generatesFourDigitNumbers() {
        for (int i = 0; i < 200; i++) {
            int value = service.generateFourDigitNumber();
            assertTrue(value >= 1000 && value <= 9999);
        }
    }
}
