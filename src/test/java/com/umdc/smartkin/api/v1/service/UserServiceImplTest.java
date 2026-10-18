package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.GetUserResponse;
import com.umdc.smartkin.api.v1.to.PutUserRequest;
import com.umdc.smartkin.api.v1.to.UserCreateResponse;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.BackboneProfileImageRefResponse;
import com.umdc.smartkin.client.backbone.to.BackboneUserCreateRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserCreateResponse;
import com.umdc.smartkin.client.backbone.to.BackboneUserUpdateRequest;
import com.umdc.smartkin.kafka.producer.EmailMessageProducerService;
import com.umdc.smartkin.kafka.to.EmailMessageTO;
import com.umdc.smartkin.mapper.GetUserMapper;
import com.umdc.smartkin.mapper.PutUserMapper;
import com.umdc.smartkin.mapper.UserCreateMapper;
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
import java.util.Arrays;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String TOKEN = "session-token";
    private static final String MESSAGE_ERROR = "message-error";
    private static final String MESSAGE = "message";

    @Mock
    private BackboneClient backboneClient;
    @Mock
    private EmailMessageProducerService producer;
    @Mock
    private UserCreateMapper userCreateMapper;
    @Mock
    private PutUserMapper putUserMapper;
    @Mock
    private GetUserMapper getUserMapper;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(backboneClient, producer, userCreateMapper, putUserMapper, getUserMapper);
        ReflectionTestUtils.setField(service, "verificationCodeTemplateId", UUID.randomUUID());
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
            var captor = ArgumentCaptor.forClass(EmailMessageTO.class);
            verify(producer).sendMessage(captor.capture());
            var email = captor.getValue();
            assertEquals("ana@smartkin.test", email.to().getFirst().email());
            assertEquals("Ana Lopez", email.to().getFirst().name());
            assertTrue(email.params().get("vc").toString().matches("\\d{4}-\\d{4}"));
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
            verifyNoInteractions(backboneClient, producer);
        }

        @Test
        void answersConflictWhenTheEmailAlreadyExists() {
            when(backboneClient.checkEmail(any(), eq(APPLICATION_ID))).thenThrow(feign(409));

            var response = service.create(userCreateRequest("Lopez", "+15550100"));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals("Email already exists", response.getHeaders().getFirst(MESSAGE_ERROR));
            verify(producer, never()).sendMessage(any());
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

            var response = service.update(USER_ID, putRequest(List.of(ROLE_ID)));

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        }

        @Test
        void updatesWithoutRoleChanges() {
            stubExistingUser();
            stubUpdate(ResponseEntity.ok().build());

            assertEquals(HttpStatus.OK, service.update(USER_ID, putRequest(null)).getStatusCode());
        }

        @Test
        void rejectsANullRequest() {
            var response = service.update(USER_ID, null);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Request body is missing.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void rejectsANullUserId() {
            var response = service.update(null, putRequest(null));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("User ID is required.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void rejectsAnEmptyRoleList() {
            var response = service.update(USER_ID, putRequest(List.of()));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getHeaders().getFirst(MESSAGE).startsWith("Cannot remove all roles"));
        }

        @Test
        void rejectsNullRoleIds() {
            var response = service.update(USER_ID, putRequest(Arrays.asList(ROLE_ID, null)));

            assertEquals("Role collection contains invalid null values.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void rejectsDuplicatedRoleIds() {
            var response = service.update(USER_ID, putRequest(List.of(ROLE_ID, ROLE_ID)));

            assertEquals("Role collection contains duplicate values.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void rejectsTheZeroRoleId() {
            var response = service.update(USER_ID, putRequest(List.of(new UUID(0, 0))));

            assertEquals("Role collection contains invalid role ID.", response.getHeaders().getFirst(MESSAGE));
            verifyNoInteractions(backboneClient);
        }

        @Test
        void answersNotFoundWhenBackboneReturnsNoUser() {
            when(backboneClient.findUserById(USER_ID)).thenReturn(null);

            var response = service.update(USER_ID, putRequest(null));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("User not found.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void answersNotFoundWhenTheLookupReturns404() {
            when(backboneClient.findUserById(USER_ID)).thenThrow(feign(404));

            assertEquals(HttpStatus.NOT_FOUND, service.update(USER_ID, putRequest(null)).getStatusCode());
        }

        @Test
        void answersServerErrorWhenTheLookupFails() {
            when(backboneClient.findUserById(USER_ID)).thenThrow(feign(502));

            var response = service.update(USER_ID, putRequest(null));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("Error updating user.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void answersNotFoundWhenTheUpdateReturns404() {
            stubExistingUser();
            when(putUserMapper.toBackbone(any(), any())).thenReturn(mock(BackboneUserUpdateRequest.class));
            when(backboneClient.userPartialUpdate(eq(USER_ID), any())).thenThrow(feign(404));

            assertEquals(HttpStatus.NOT_FOUND, service.update(USER_ID, putRequest(null)).getStatusCode());
        }

        @Test
        void answersBadRequestWhenTheUpdateReturns400() {
            stubExistingUser();
            when(putUserMapper.toBackbone(any(), any())).thenReturn(mock(BackboneUserUpdateRequest.class));
            when(backboneClient.userPartialUpdate(eq(USER_ID), any())).thenThrow(feign(400));

            var response = service.update(USER_ID, putRequest(null));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid request data.", response.getHeaders().getFirst(MESSAGE));
        }

        @Test
        void answersServerErrorOnUnexpectedFailures() {
            stubExistingUser();
            when(putUserMapper.toBackbone(any(), any())).thenThrow(new IllegalStateException("boom"));

            var response = service.update(USER_ID, putRequest(null));

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

            assertEquals(HttpStatus.NO_CONTENT, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        }

        @Test
        void returnsNotModifiedForAnyOtherSuccessStatus() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(APPLICATION_ID, USER_ID))
                    .thenReturn(ResponseEntity.ok().build());

            assertEquals(HttpStatus.NOT_MODIFIED, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        }

        @Test
        void relaysNotFound() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(404));

            assertEquals(HttpStatus.NOT_FOUND, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        }

        @Test
        void relaysForbidden() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(403));

            assertEquals(HttpStatus.FORBIDDEN, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        }

        @Test
        void relaysBadRequest() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(400));

            assertEquals(HttpStatus.BAD_REQUEST, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        }

        @Test
        void answersServerErrorForOtherBackboneStatuses() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(feign(502));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        }

        @Test
        void answersServerErrorOnUnexpectedFailures() {
            when(backboneClient.deleteUserByUserIdAndApplicationId(any(), any())).thenThrow(new IllegalStateException("boom"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, service.deleteUserByUserAndApplication(USER_ID).getStatusCode());
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
