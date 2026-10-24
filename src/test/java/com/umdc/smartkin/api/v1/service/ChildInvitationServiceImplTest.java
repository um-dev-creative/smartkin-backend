package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.invitation.InvitationCode;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ChildInvitationEntity;
import com.umdc.smartkin.jpa.domain.InvitationStatus;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildInvitationRepository;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_ALREADY_LINKED_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.INVITATION_ALREADY_ACCEPTED_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.INVITATION_CODE_ATTEMPTS_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.INVITATION_LIMIT_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.INVITATION_NOT_FOUND_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MAX_PENDING_INVITATIONS;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.NOT_A_PARENT_MESSAGE;
import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildInvitationServiceImplTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID INVITATION_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ChildRepository childRepository;
    @Mock
    private ChildInvitationRepository invitationRepository;
    private ChildInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        var linker = new ChildLinker(parentRepository, childRepository);
        service = new ChildInvitationServiceImpl(parentRepository, invitationRepository, linker, new ChildMapperImpl(),
                new AccessPolicy(parentRepository, childRepository), new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ParentEntity parent() {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        return parent;
    }

    private static ChildInvitationEntity invitation(InvitationStatus status, Instant expiresAt) {
        var invitation = new ChildInvitationEntity();
        invitation.setId(INVITATION_ID);
        invitation.setParent(parent());
        invitation.setStatus(status);
        invitation.setExpiresAt(expiresAt);
        invitation.setCreatedAt(NOW.minusSeconds(60));
        invitation.setGradeLevel("5");
        invitation.setAvatar("fox.png");
        invitation.setCodeHash("hash");
        return invitation;
    }

    private void callerIsParent() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent()));
    }

    // create

    @Test
    void createStoresOnlyTheHashAndReturnsTheCodeOnce() {
        callerIsParent();
        when(invitationRepository.save(any(ChildInvitationEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.create(tokenFor(PARENT_ID), new InvitationRequest("4", "owl.png"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var code = response.getBody().code();
        assertTrue(code.matches("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}"), code);
        var saved = ArgumentCaptor.forClass(ChildInvitationEntity.class);
        verify(invitationRepository).save(saved.capture());
        assertEquals(InvitationCode.hash(InvitationCode.normalize(code).orElseThrow()), saved.getValue().getCodeHash());
        assertEquals(NOW.plusSeconds(7 * 24 * 3600), saved.getValue().getExpiresAt());
        assertEquals("PENDING", response.getBody().status());
        assertEquals("4", response.getBody().gradeLevel());
    }

    @Test
    void createRefusesACallerWithoutParentProfileOrToken() {
        when(parentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        var notParent = service.create(tokenFor(PARENT_ID), new InvitationRequest(null, null));
        var unknown = service.create("garbage", new InvitationRequest(null, null));

        assertEquals(HttpStatus.FORBIDDEN, notParent.getStatusCode());
        assertEquals(NOT_A_PARENT_MESSAGE, notParent.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertEquals(CALLER_UNKNOWN_MESSAGE, unknown.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void createRefusesWhenTooManyInvitationsArePending() {
        callerIsParent();
        when(invitationRepository.countByParentIdAndStatusAndExpiresAtAfter(PARENT_ID, InvitationStatus.PENDING, NOW))
                .thenReturn((long) MAX_PENDING_INVITATIONS);

        var response = service.create(tokenFor(PARENT_ID), new InvitationRequest(null, null));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(INVITATION_LIMIT_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    @Test
    void createDrawsAnotherCodeWhenOneClashes() {
        callerIsParent();
        when(invitationRepository.existsByCodeHash(anyString())).thenReturn(true, false);
        when(invitationRepository.save(any(ChildInvitationEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals(HttpStatus.CREATED, service.create(tokenFor(PARENT_ID), new InvitationRequest(null, null)).getStatusCode());
        verify(invitationRepository, org.mockito.Mockito.times(2)).existsByCodeHash(anyString());
    }

    @Test
    void createGivesUpWhenEveryDrawClashes() {
        callerIsParent();
        when(invitationRepository.existsByCodeHash(anyString())).thenReturn(true);

        var response = service.create(tokenFor(PARENT_ID), new InvitationRequest(null, null));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(INVITATION_CODE_ATTEMPTS_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    // list

    @Test
    void listShowsTheStatusWithoutTheCodeAndMarksExpiredOnes() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        when(invitationRepository.findAllByParentIdOrderByCreatedAtDesc(PARENT_ID)).thenReturn(List.of(
                invitation(InvitationStatus.PENDING, NOW.plusSeconds(10)),
                invitation(InvitationStatus.PENDING, NOW.minusSeconds(1)),
                invitation(InvitationStatus.ACCEPTED, NOW.minusSeconds(1))));

        var body = service.list(tokenFor(PARENT_ID)).getBody();

        assertEquals(List.of("PENDING", "EXPIRED", "ACCEPTED"), body.stream().map(item -> item.status()).toList());
        assertTrue(body.stream().allMatch(item -> item.code() == null));
    }

    @Test
    void listRefusesANonParentAndAnUnknownCaller() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, service.list(tokenFor(PARENT_ID)).getStatusCode());
        assertEquals(CALLER_UNKNOWN_MESSAGE, service.list(null).getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    // revoke

    @Test
    void revokeMarksAPendingInvitation() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        var pending = invitation(InvitationStatus.PENDING, NOW.plusSeconds(10));
        when(invitationRepository.findByIdAndParentId(INVITATION_ID, PARENT_ID)).thenReturn(Optional.of(pending));

        assertEquals(HttpStatus.NO_CONTENT, service.revoke(tokenFor(PARENT_ID), INVITATION_ID).getStatusCode());
        assertEquals(InvitationStatus.REVOKED, pending.getStatus());
        verify(invitationRepository).save(pending);
    }

    @Test
    void revokeHidesTheInvitationOfAnotherParentAndRefusesAcceptedOnes() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        when(invitationRepository.findByIdAndParentId(INVITATION_ID, PARENT_ID)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, service.revoke(tokenFor(PARENT_ID), INVITATION_ID).getStatusCode());

        when(invitationRepository.findByIdAndParentId(INVITATION_ID, PARENT_ID))
                .thenReturn(Optional.of(invitation(InvitationStatus.ACCEPTED, NOW.plusSeconds(10))));
        var accepted = service.revoke(tokenFor(PARENT_ID), INVITATION_ID);
        assertEquals(HttpStatus.CONFLICT, accepted.getStatusCode());
        assertEquals(INVITATION_ALREADY_ACCEPTED_MESSAGE, accepted.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void revokeRefusesANonParentAndAnUnknownCaller() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, service.revoke(tokenFor(PARENT_ID), INVITATION_ID).getStatusCode());
        assertEquals(CALLER_UNKNOWN_MESSAGE, service.revoke("garbage", INVITATION_ID).getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }

    // accept

    @Test
    void acceptTurnsTheCallerIntoAChildOfTheInviter() {
        var pending = invitation(InvitationStatus.PENDING, NOW.plusSeconds(10));
        when(invitationRepository.findByCodeHash(InvitationCode.hash("ABCDEFGH"))).thenReturn(Optional.of(pending));
        when(invitationRepository.claim(INVITATION_ID, CHILD_ID, NOW)).thenReturn(1);
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.accept(tokenFor(CHILD_ID), new AcceptInvitationRequest("abcd-efgh"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(CHILD_ID, response.getBody().childId());
        assertEquals(PARENT_ID, response.getBody().parentId());
        assertEquals("5", response.getBody().gradeLevel());
        assertEquals("fox.png", response.getBody().avatar());
    }

    @Test
    void acceptAnswers404AlikeForUnknownMalformedAndAlreadyUsedCodes() {
        when(invitationRepository.findByCodeHash(anyString())).thenReturn(Optional.empty());
        var used = invitation(InvitationStatus.ACCEPTED, NOW.plusSeconds(10));
        when(invitationRepository.findByCodeHash(InvitationCode.hash("ABCDEFGH"))).thenReturn(Optional.of(used));
        when(invitationRepository.claim(INVITATION_ID, CHILD_ID, NOW)).thenReturn(0);

        var unknown = service.accept(tokenFor(CHILD_ID), new AcceptInvitationRequest("ZZZZ-ZZZZ"));
        var malformed = service.accept(tokenFor(CHILD_ID), new AcceptInvitationRequest("nope"));
        var claimed = service.accept(tokenFor(CHILD_ID), new AcceptInvitationRequest("ABCDEFGH"));

        for (var response : List.of(unknown, malformed, claimed)) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(INVITATION_NOT_FOUND_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        }
        verify(childRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptRefusesACallerWhoAlreadyHasAProfileBeforeTouchingTheCode() {
        when(parentRepository.existsById(CHILD_ID)).thenReturn(true);

        var response = service.accept(tokenFor(CHILD_ID), new AcceptInvitationRequest("ABCD-EFGH"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(CHILD_ALREADY_LINKED_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        verify(invitationRepository, never()).claim(any(), any(), any());
    }

    @Test
    void acceptRefusesAnUnknownCaller() {
        var response = service.accept("garbage", new AcceptInvitationRequest("ABCD-EFGH"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(CALLER_UNKNOWN_MESSAGE, response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
        assertNull(response.getBody());
    }

    @Test
    void acceptAnswers409WhenTheProfileIsCreatedConcurrently() {
        var pending = invitation(InvitationStatus.PENDING, NOW.plusSeconds(10));
        when(invitationRepository.findByCodeHash(InvitationCode.hash("ABCDEFGH"))).thenReturn(Optional.of(pending));
        when(invitationRepository.claim(INVITATION_ID, CHILD_ID, NOW)).thenReturn(1);
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = service.accept(tokenFor(CHILD_ID), new AcceptInvitationRequest("ABCDEFGH"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getHeaders().getFirst(MESSAGE_ERROR_HEADER));
    }
}
