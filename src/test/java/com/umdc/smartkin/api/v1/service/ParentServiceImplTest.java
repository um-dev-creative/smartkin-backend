package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ParentMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_CANNOT_BE_PARENT_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.PARENT_PROFILE_NOT_FOUND_MESSAGE;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParentServiceImplTest {

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ChildRepository childRepository;
    private ParentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParentServiceImpl(parentRepository, childRepository, new ParentMapperImpl());
    }

    private static ParentEntity parent() {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        parent.setPhoneNumber("+15550100");
        parent.setAddress("Main St");
        parent.setProfilePicture("me.png");
        return parent;
    }

    @Test
    void getMeReturnsTheProfileOfTheCaller() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent()));

        var response = service.getMe(tokenFor(PARENT_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(PARENT_ID, response.getBody().parentId());
        assertEquals("+15550100", response.getBody().phoneNumber());
        assertEquals("Main St", response.getBody().address());
        assertEquals("me.png", response.getBody().profilePicture());
    }

    @Test
    void getMeAnswers404WhenThereIsNoProfileYet() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());
        when(childRepository.existsById(PARENT_ID)).thenReturn(false);

        var response = service.getMe(tokenFor(PARENT_ID));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(PARENT_PROFILE_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertNull(response.getBody());
    }

    @Test
    void getMeAnswers403ToAChild() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());
        when(childRepository.existsById(PARENT_ID)).thenReturn(true);

        var response = service.getMe(tokenFor(PARENT_ID));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(CHILD_CANNOT_BE_PARENT_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    @Test
    void getMeAnswers403WhenTheTokenHasNoUser() {
        var response = service.getMe("not-a-token");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(CALLER_UNKNOWN_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    @Test
    void putMeCreatesTheProfileTheFirstTime() {
        when(childRepository.existsById(PARENT_ID)).thenReturn(false);
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());
        when(parentRepository.save(any(ParentEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.putMe(tokenFor(PARENT_ID), new ParentProfileRequest("+15550111", "Elm St", "p.png"));

        var saved = ArgumentCaptor.forClass(ParentEntity.class);
        verify(parentRepository).save(saved.capture());
        assertEquals(PARENT_ID, saved.getValue().getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Elm St", response.getBody().address());
    }

    @Test
    void putMeReplacesTheDataOfAnExistingProfile() {
        when(childRepository.existsById(PARENT_ID)).thenReturn(false);
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent()));
        when(parentRepository.save(any(ParentEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.putMe(tokenFor(PARENT_ID), new ParentProfileRequest(null, null, null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().phoneNumber());
        assertNull(response.getBody().address());
        assertNull(response.getBody().profilePicture());
    }

    @Test
    void putMeRefusesAChildAndAnUnknownCaller() {
        when(childRepository.existsById(PARENT_ID)).thenReturn(true);

        var child = service.putMe(tokenFor(PARENT_ID), new ParentProfileRequest(null, null, null));
        var unknown = service.putMe(null, new ParentProfileRequest(null, null, null));

        assertEquals(HttpStatus.FORBIDDEN, child.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(parentRepository, never()).save(any());
    }
}
