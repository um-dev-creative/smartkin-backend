package com.umdc.smartkin.mapper;

import com.umdc.commons.general.pojo.Application;
import com.umdc.commons.general.pojo.Contact;
import com.umdc.commons.general.pojo.Person;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.PutUserRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserCreateResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.DEFAULT_GENDER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.PHONE_CONTACT_TYPE_ID;
import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.PHONE_ID;
import static com.umdc.smartkin.support.TestData.ROLE_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.application;
import static com.umdc.smartkin.support.TestData.backboneUser;
import static com.umdc.smartkin.support.TestData.person;
import static com.umdc.smartkin.support.TestData.role;
import static com.umdc.smartkin.support.TestData.userCreateRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappersTest {

    private final UserCreateMapper userCreateMapper = new UserCreateMapperImpl();
    private final GetUserMapper getUserMapper = new GetUserMapperImpl();
    private final PutUserMapper putUserMapper = new PutUserMapperImpl();
    private final ConfirmCodeMapper confirmCodeMapper = new ConfirmCodeMapperImpl();

    @Test
    void userCreateMapperBuildsTheBackboneRequestWithThePhoneContact() {
        var request = userCreateMapper.toBackbone(userCreateRequest("Lopez", "+15550100"), APPLICATION_ID, ROLE_ID, "alopez");

        assertEquals("alopez", request.alias());
        assertEquals("ana@smartkin.test", request.email());
        assertEquals(APPLICATION_ID, request.applicationId());
        assertEquals(ROLE_ID, request.roleId());
        assertEquals("Ana", request.person().getFirstName());
        assertEquals("Lopez", request.person().getLastName());
        assertEquals(DEFAULT_GENDER, request.person().getGender());
        var contact = request.person().getContacts().getFirst();
        assertEquals("+15550100", contact.getContent());
        assertEquals(PHONE_CONTACT_TYPE_ID, contact.getContactType().getId());
        assertTrue(contact.getActive());
    }

    @Test
    void userCreateMapperSkipsTheContactWhenThereIsNoPhone() {
        var person = userCreateMapper.getPerson(userCreateRequest("Lopez", " "));

        assertTrue(person.getContacts() == null || person.getContacts().isEmpty());
    }

    @Test
    void userCreateMapperMapsTheBackboneResponse() {
        var created = LocalDateTime.of(2026, 1, 1, 0, 0);
        var backbone = new BackboneUserCreateResponse(USER_ID, "alopez", "ana@smartkin.test", created, created, true,
                UUID.randomUUID(), ROLE_ID, APPLICATION_ID, "Ana", true, true, false);

        var response = userCreateMapper.fromBackbone(backbone);

        assertEquals(USER_ID, response.id());
        assertEquals("alopez", response.alias());
        assertEquals(ROLE_ID, response.roleId());
        assertTrue(response.active());
    }

    @Test
    void getUserMapperMapsPersonRolesAndApplication() {
        var response = getUserMapper.fromBackbone(
                backboneUser(person("Phone"), List.of(role(ROLE_ID)), List.of(application(APPLICATION_ID))), "images/ana.png");

        assertEquals(USER_ID, response.id());
        assertEquals("Ana", response.firstName());
        assertEquals("Lopez", response.lastName());
        assertEquals("+15550100", response.phoneNumber());
        assertEquals(PHONE_ID, response.phoneId());
        assertEquals(ROLE_ID, response.roleId());
        assertEquals(APPLICATION_ID, response.applicationId());
        assertEquals("images/ana.png", response.profileImageRef());
        assertTrue(response.status());
    }

    @Test
    void getUserMapperHandlesMissingRolesApplicationsAndPhones() {
        var user = backboneUser(person("Email"), null, List.of());

        assertNull(getUserMapper.getRoleId(user));
        assertNull(getUserMapper.getApplicationId(user));
        assertNull(getUserMapper.getContactId(user.person()));
        assertNull(getUserMapper.getPhone(user.person()));
        assertEquals("", getUserMapper.getPhone(null));
        assertNull(getUserMapper.getContactId(null));
        assertNull(getUserMapper.getRoleId(backboneUser(person("Phone"), List.of(), null)));
        assertNull(getUserMapper.getApplicationId(backboneUser(person("Phone"), null, null)));
    }

    @Test
    void getUserMapperSkipsEntriesWithoutId() {
        var user = backboneUser(person("Phone"), List.of(role(null)), List.of(new Application()));

        assertNull(getUserMapper.getRoleId(user));
        assertNull(getUserMapper.getApplicationId(user));
    }

    @Test
    void getUserMapperFindsThePhoneContact() {
        Person person = person("Phone");
        Contact other = person.getContacts().getFirst();

        assertEquals(other.getId(), getUserMapper.getContactId(person));
    }

    @Test
    void putUserMapperBuildsTheBackboneUpdateWithThePhoneContact() {
        var request = new PutUserRequest("Ana", "Lopez", "Ana L", true, false, true, PHONE_ID, "+15550101", List.of(ROLE_ID), true);

        var update = putUserMapper.toBackbone(APPLICATION_ID, request);

        assertEquals(APPLICATION_ID, update.application());
        assertEquals("Ana L", update.displayName());
        assertEquals(List.of(ROLE_ID), update.roleIds());
        assertEquals(Boolean.TRUE, update.active());
        var contact = update.contacts().getFirst();
        assertEquals(PHONE_ID, contact.id());
        assertEquals("+15550101", contact.content());
        assertEquals(PHONE_CONTACT_TYPE_ID, contact.contactType().id());
    }

    @Test
    void confirmCodeMapperBuildsTheVerificationRequest() {
        var request = confirmCodeMapper.toVerificationCodeRequest(new ConfirmCodeRequest(USER_ID, "1234-5678"), APPLICATION_ID);

        assertEquals(USER_ID, request.userId());
        assertEquals("1234-5678", request.code());
        assertEquals(APPLICATION_ID, request.applicationId());
    }
}
