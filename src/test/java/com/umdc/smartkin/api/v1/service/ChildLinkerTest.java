package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildLinkerTest {

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private ChildRepository childRepository;
    private ChildLinker linker;

    @BeforeEach
    void setUp() {
        linker = new ChildLinker(parentRepository, childRepository);
    }

    @Test
    void aUserWithoutProfileIsFree() {
        assertTrue(linker.isFree(CHILD_ID));
    }

    @Test
    void aChildOrAParentIsNotFree() {
        when(childRepository.existsById(CHILD_ID)).thenReturn(true);
        assertFalse(linker.isFree(CHILD_ID));

        when(childRepository.existsById(PARENT_ID)).thenReturn(false);
        when(parentRepository.existsById(PARENT_ID)).thenReturn(true);
        assertFalse(linker.isFree(PARENT_ID));
    }

    @Test
    void createSavesTheChildWithZeroPointsAndFlushes() {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        when(childRepository.saveAndFlush(any(ChildEntity.class))).thenAnswer(call -> call.getArgument(0));

        var child = linker.create(parent, CHILD_ID, "4", "owl.png");

        assertEquals(CHILD_ID, child.getId());
        assertSame(parent, child.getParent());
        assertEquals("4", child.getGradeLevel());
        assertEquals("owl.png", child.getAvatar());
        assertEquals(0, child.getTotalPoints());
    }
}
