package com.umdc.smartkin.mapper;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import org.junit.jupiter.api.Test;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ParentChildMappersTest {

    @Test
    void mapsTheParentEntity() {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        parent.setPhoneNumber("+1");
        parent.setAddress("Elm");
        parent.setProfilePicture("p.png");

        var response = new ParentMapperImpl().toResponse(parent);

        assertEquals(PARENT_ID, response.parentId());
        assertEquals("+1", response.phoneNumber());
        assertEquals("Elm", response.address());
        assertEquals("p.png", response.profilePicture());
    }

    @Test
    void mapsTheChildEntityWithItsParentId() {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        var child = new ChildEntity();
        child.setId(CHILD_ID);
        child.setParent(parent);
        child.setGradeLevel("5");
        child.setAvatar("fox.png");
        child.setTotalPoints(40);

        var response = new ChildMapperImpl().toResponse(child);

        assertEquals(CHILD_ID, response.childId());
        assertEquals(PARENT_ID, response.parentId());
        assertEquals("5", response.gradeLevel());
        assertEquals("fox.png", response.avatar());
        assertEquals(40, response.totalPoints());
    }
}
