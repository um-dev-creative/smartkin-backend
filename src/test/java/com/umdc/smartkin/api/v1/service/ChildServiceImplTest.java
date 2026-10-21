package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildRequest;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_ALREADY_LINKED_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_IS_CALLER_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_USER_NOT_FOUND_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.NOT_A_PARENT_MESSAGE;
import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.feign;
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
    @Mock
    private BackboneClient backboneClient;
    private ChildServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChildServiceImpl(parentRepository, childRepository, backboneClient, new ChildMapperImpl());
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

    // link

    @Test
    void linkCreatesTheChildWithZeroPoints() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(PARENT_ID)));
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, "4", "owl.png"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(CHILD_ID, response.getBody().childId());
        assertEquals(PARENT_ID, response.getBody().parentId());
        assertEquals("4", response.getBody().gradeLevel());
        assertEquals(0, response.getBody().totalPoints());
        verify(backboneClient).findUserById(CHILD_ID);
    }

    @Test
    void linkRefusesACallerWithoutParentProfileOrToken() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        var notParent = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, null, null));
        var unknown = service.link(null, new ChildRequest(CHILD_ID, null, null));

        assertEquals(HttpStatus.FORBIDDEN, notParent.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, unknown.getStatusCode());
        verify(childRepository, never()).saveAndFlush(any());
    }

    @Test
    void linkRefusesTheCallerAsTheirOwnChild() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(PARENT_ID)));

        var response = service.link(tokenFor(PARENT_ID), new ChildRequest(PARENT_ID, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(CHILD_IS_CALLER_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    @Test
    void linkAnswers409WhenTheUserAlreadyHasAProfile() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(PARENT_ID)));
        when(childRepository.existsById(CHILD_ID)).thenReturn(true);

        var alreadyChild = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, null, null));

        when(childRepository.existsById(CHILD_ID)).thenReturn(false);
        when(parentRepository.existsById(CHILD_ID)).thenReturn(true);
        var alreadyParent = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, null, null));

        assertEquals(HttpStatus.CONFLICT, alreadyChild.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, alreadyParent.getStatusCode());
        assertEquals(CHILD_ALREADY_LINKED_MESSAGE, alreadyParent.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(backboneClient, never()).findUserById(any());
    }

    @Test
    void linkAnswers400WhenBackboneDoesNotKnowTheUser() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(PARENT_ID)));
        when(backboneClient.findUserById(CHILD_ID)).thenThrow(feign(404));

        var response = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(CHILD_USER_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(childRepository, never()).saveAndFlush(any());
    }

    @Test
    void linkRelaysOtherBackboneFailures() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(PARENT_ID)));
        when(backboneClient.findUserById(CHILD_ID)).thenThrow(feign(503));

        var response = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, null, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void linkAnswers409WhenTwoParentsLinkTheSameChildAtOnce() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent(PARENT_ID)));
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = service.link(tokenFor(PARENT_ID), new ChildRequest(CHILD_ID, null, null));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(CHILD_ALREADY_LINKED_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
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
