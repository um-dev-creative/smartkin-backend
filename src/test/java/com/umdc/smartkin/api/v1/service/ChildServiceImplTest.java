package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE;
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
class ChildServiceImplTest {

    private static final UUID OTHER_PARENT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ChildRepository childRepository;
    private ChildServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChildServiceImpl(childRepository, new ChildMapperImpl(), new AccessPolicy(parentRepository, childRepository));
    }

    private static ParentEntity parent(UUID id) {
        var parent = new ParentEntity();
        parent.setId(id);
        return parent;
    }

    private static ChildEntity child(UUID parentId) {
        var child = new ChildEntity();
        child.setId(CHILD_ID);
        child.setParent(parent(parentId));
        child.setGradeLevel("5");
        child.setAvatar("fox.png");
        child.setTotalPoints(30);
        return child;
    }

    private void callerIsParent() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
    }

    // list

    @Test
    void listReturnsTheChildrenOfTheParent() {
        callerIsParent();
        when(childRepository.findAllByParentId(PARENT_ID)).thenReturn(List.of(child(PARENT_ID)));

        var response = service.list(tokenFor(PARENT_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(CHILD_ID, response.getBody().get(0).childId());
        assertEquals(PARENT_ID, response.getBody().get(0).parentId());
        assertEquals(30, response.getBody().get(0).totalPoints());
    }

    @Test
    void listRefusesANonParentAndAnUnknownCaller() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(false);

        var notParent = service.list(tokenFor(PARENT_ID));
        var unknown = service.list("garbage");

        assertEquals(HttpStatus.FORBIDDEN, notParent.getStatusCode());
        assertEquals(NOT_A_PARENT_MESSAGE, notParent.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertEquals(CALLER_UNKNOWN_MESSAGE, unknown.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    // get

    @Test
    void getReturnsTheChildToTheirParent() {
        callerIsParent();
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child(PARENT_ID)));

        var response = service.get(tokenFor(PARENT_ID), CHILD_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(CHILD_ID, response.getBody().childId());
    }

    @Test
    void getReturnsTheChildToTheChildThemselves() {
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child(PARENT_ID)));

        var response = service.get(tokenFor(CHILD_ID), CHILD_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getHidesTheChildOfAnotherFamilyBehind404() {
        when(parentRepository.existsById(OTHER_PARENT_ID)).thenReturn(true);
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child(PARENT_ID)));

        var response = service.get(tokenFor(OTHER_PARENT_ID), CHILD_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(CHILD_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertNull(response.getBody());
    }

    @Test
    void getAnswers404ForAMissingChildAndRefusesOtherCallers() {
        callerIsParent();
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.empty());
        var missing = service.get(tokenFor(PARENT_ID), CHILD_ID);

        var siblingId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(parentRepository.existsById(siblingId)).thenReturn(false);
        var stranger = service.get(tokenFor(siblingId), CHILD_ID);
        var unknown = service.get("garbage", CHILD_ID);

        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, stranger.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
    }

    // update

    @Test
    void updateReplacesTheGradeAndAvatarOfAnOwnedChild() {
        callerIsParent();
        var entity = child(PARENT_ID);
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(entity));
        when(childRepository.save(entity)).thenReturn(entity);

        var response = service.update(tokenFor(PARENT_ID), CHILD_ID, new ChildUpdateRequest("6", null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("6", response.getBody().gradeLevel());
        assertNull(response.getBody().avatar());
        assertEquals(30, response.getBody().totalPoints());
    }

    @Test
    void updateDoesNotTouchAChildOfAnotherFamily() {
        when(parentRepository.existsById(OTHER_PARENT_ID)).thenReturn(true);
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child(PARENT_ID)));

        var response = service.update(tokenFor(OTHER_PARENT_ID), CHILD_ID, new ChildUpdateRequest("9", "x.png"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(childRepository, never()).save(any());
    }

    @Test
    void updateRefusesAChildAndAnUnknownCaller() {
        when(parentRepository.existsById(CHILD_ID)).thenReturn(false);

        var child = service.update(tokenFor(CHILD_ID), CHILD_ID, new ChildUpdateRequest("9", null));
        var unknown = service.update(null, CHILD_ID, new ChildUpdateRequest("9", null));

        assertEquals(HttpStatus.FORBIDDEN, child.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(childRepository, never()).save(any());
    }
}
