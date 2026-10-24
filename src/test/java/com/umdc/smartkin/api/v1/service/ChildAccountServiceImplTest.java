package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildAccountRequest;
import com.umdc.smartkin.api.v1.to.UserCreateRequest;
import com.umdc.smartkin.api.v1.to.UserCreateResponse;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_ACCOUNT_FAILED_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_MUST_BE_MINOR_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.NOT_A_PARENT_MESSAGE;
import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildAccountServiceImplTest {

    private static final Instant NOW = Instant.parse("2030-06-15T00:00:00Z");

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ChildRepository childRepository;
    @Mock
    private UserService userService;
    private ChildAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChildAccountServiceImpl(parentRepository, userService, new ChildLinker(parentRepository, childRepository),
                new ChildMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ChildAccountRequest request(LocalDate birth) {
        return new ChildAccountRequest("Ana", "Roe", birth, "ana@smartkin.test", "secret-pass", "3", "fox.png");
    }

    private static ParentEntity parent(String phone) {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        parent.setPhoneNumber(phone);
        return parent;
    }

    private static UserCreateResponse created() {
        return new UserCreateResponse(CHILD_ID, "aroe", "ana@smartkin.test", null, null, true, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, null, null, null);
    }

    @Test
    void createMakesTheUserThenLinksItToTheParent() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent("+15550100")));
        when(userService.create(any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(created()));
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("aroe", response.getBody().alias());
        assertEquals("ana@smartkin.test", response.getBody().email());
        assertEquals(CHILD_ID, response.getBody().child().childId());
        assertEquals(PARENT_ID, response.getBody().child().parentId());
        assertEquals("3", response.getBody().child().gradeLevel());
        var sent = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).create(sent.capture());
        assertEquals("+15550100", sent.getValue().phoneNumber());
        assertEquals("Ana", sent.getValue().firstname());
        assertEquals(LocalDate.of(2018, 4, 2), sent.getValue().dateOfBirth());
    }

    @Test
    void createWorksWhenTheParentHasNoPhone() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(null)));
        when(userService.create(any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(created()));
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));

        var sent = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).create(sent.capture());
        assertEquals("", sent.getValue().phoneNumber());
    }

    @Test
    void createRefusesAdultsAndFutureBirthDatesWithoutCreatingAUser() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(null)));

        for (var birth : new LocalDate[]{LocalDate.of(2012, 6, 15), LocalDate.of(1990, 1, 1), LocalDate.of(2030, 6, 15), LocalDate.of(2031, 1, 1)}) {
            var response = service.create(tokenFor(PARENT_ID), request(birth));
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), birth.toString());
            assertEquals(CHILD_MUST_BE_MINOR_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        }
        verify(userService, never()).create(any());
    }

    @Test
    void createAcceptsTheDayBeforeTheEighteenthBirthday() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(null)));
        when(userService.create(any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(created()));
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals(HttpStatus.CREATED, service.create(tokenFor(PARENT_ID), request(LocalDate.of(2012, 6, 16))).getStatusCode());
    }

    @Test
    void createRefusesACallerWithoutParentProfileOrToken() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        var notParent = service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));
        var unknown = service.create("garbage", request(LocalDate.of(2018, 4, 2)));

        assertEquals(HttpStatus.FORBIDDEN, notParent.getStatusCode());
        assertEquals(NOT_A_PARENT_MESSAGE, notParent.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertEquals(CALLER_UNKNOWN_MESSAGE, unknown.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(userService, never()).create(any());
    }

    @Test
    void createRelaysTheUserServiceFailureWithItsMessage() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(null)));
        when(userService.create(any())).thenReturn(ResponseEntity.status(HttpStatus.CONFLICT).header(MESSAGE_ERROR_HEADER, "Email already exists").build());

        var response = service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Email already exists", response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(childRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRelaysAFailureWithoutMessageAndAnEmptySuccess() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(null)));
        when(userService.create(any())).thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build())
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        var failure = service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));
        var empty = service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));

        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
        assertNull(failure.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.CREATED, empty.getStatusCode());
        assertNull(empty.getBody());
    }

    @Test
    void createRemovesTheNewUserWhenTheProfileCannotBeSaved() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(null)));
        when(userService.create(any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(created()));
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenThrow(new IllegalStateException("db down"));

        var response = service.create(tokenFor(PARENT_ID), request(LocalDate.of(2018, 4, 2)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(CHILD_ACCOUNT_FAILED_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(userService).deleteUserByUserAndApplication(CHILD_ID);
    }
}
