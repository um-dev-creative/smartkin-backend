package com.umdc.smartkin.security.access;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessPolicyTest {

    private static final UUID OTHER_PARENT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID SIBLING_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ChildRepository childRepository;
    private AccessPolicy policy;
    private ChildEntity child;

    @BeforeEach
    void setUp() {
        policy = new AccessPolicy(parentRepository, childRepository);
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        child = new ChildEntity();
        child.setId(CHILD_ID);
        child.setParent(parent);
    }

    @Test
    void tellsParentsAndChildrenApart() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        when(childRepository.existsById(PARENT_ID)).thenReturn(false);
        when(parentRepository.existsById(CHILD_ID)).thenReturn(false);
        when(childRepository.existsById(CHILD_ID)).thenReturn(true);

        assertTrue(policy.isParent(PARENT_ID));
        assertFalse(policy.isChild(PARENT_ID));
        assertFalse(policy.isParent(CHILD_ID));
        assertTrue(policy.isChild(CHILD_ID));
    }

    @Test
    void aChildViewsThemselves() {
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child));

        var access = policy.viewChild(CHILD_ID, CHILD_ID);

        assertEquals(ChildAccessDecision.SELF, access.decision());
        assertSame(child, access.child());
        assertTrue(access.allowed());
    }

    @Test
    void aMissingChildIsNotVisibleToThemselves() {
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.empty());

        var access = policy.viewChild(CHILD_ID, CHILD_ID);

        assertEquals(ChildAccessDecision.NOT_VISIBLE, access.decision());
        assertNull(access.child());
        assertFalse(access.allowed());
    }

    @Test
    void theOwnerParentViewsAndManagesTheChild() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child));

        var view = policy.viewChild(PARENT_ID, CHILD_ID);
        var manage = policy.manageChild(PARENT_ID, CHILD_ID);

        assertEquals(ChildAccessDecision.OWNER, view.decision());
        assertEquals(ChildAccessDecision.OWNER, manage.decision());
        assertSame(child, manage.child());
        assertTrue(view.allowed() && manage.allowed());
    }

    @Test
    void anotherFamilysParentNeitherViewsNorManagesTheChild() {
        when(parentRepository.existsById(OTHER_PARENT_ID)).thenReturn(true);
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child));

        assertEquals(ChildAccessDecision.NOT_VISIBLE, policy.viewChild(OTHER_PARENT_ID, CHILD_ID).decision());
        assertEquals(ChildAccessDecision.NOT_VISIBLE, policy.manageChild(OTHER_PARENT_ID, CHILD_ID).decision());
        assertNull(policy.viewChild(OTHER_PARENT_ID, CHILD_ID).child());
    }

    @Test
    void aParentAskingForAMissingChildGetsNotVisible() {
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.empty());

        assertEquals(ChildAccessDecision.NOT_VISIBLE, policy.viewChild(PARENT_ID, CHILD_ID).decision());
    }

    @Test
    void aSiblingCannotViewAnotherChildAndNoChildManagesAnyone() {
        when(parentRepository.existsById(SIBLING_ID)).thenReturn(false);
        when(parentRepository.existsById(CHILD_ID)).thenReturn(false);

        assertEquals(ChildAccessDecision.FORBIDDEN, policy.viewChild(SIBLING_ID, CHILD_ID).decision());
        assertEquals(ChildAccessDecision.FORBIDDEN, policy.manageChild(SIBLING_ID, CHILD_ID).decision());
        assertEquals(ChildAccessDecision.FORBIDDEN, policy.manageChild(CHILD_ID, CHILD_ID).decision());
    }

    @Test
    void aDeniedAccessCarriesNoChild() {
        assertFalse(new ChildAccess(ChildAccessDecision.FORBIDDEN, null).allowed());
        assertFalse(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null).allowed());
    }
}
