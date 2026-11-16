package com.umdc.smartkin;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.GetUserResponse;
import com.umdc.smartkin.api.v1.to.UserCreateRequest;
import com.umdc.smartkin.api.v1.to.UserCreateResponse;
import com.umdc.smartkin.client.backbone.to.BackboneTokenRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserCreateRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserUpdateRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserUpdateResponse;
import com.umdc.smartkin.client.backbone.to.PostProfileImageResponse;
import com.umdc.smartkin.client.mercury.to.VerificationCodeRequest;
import com.umdc.smartkin.client.mercury.to.MercuryMessageRequest;
import com.umdc.smartkin.client.mercury.to.MercuryMessageResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.ROLE_ID;
import static com.umdc.smartkin.support.TestData.USER_ID;
import static com.umdc.smartkin.support.TestData.backboneUser;
import static com.umdc.smartkin.support.TestData.person;
import static com.umdc.smartkin.support.TestData.userCreateRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Checks the transfer objects: their `toString` output and their JSON contract under Jackson 3.
class TransferObjectsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void toStringExposesTheMainFields() {
        var now = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        var createResponse = new UserCreateResponse(USER_ID, "alopez", "ana@smartkin.test", now, now, true,
                UUID.randomUUID(), ROLE_ID, APPLICATION_ID, "Ana", true, true, false);
        var getResponse = new GetUserResponse(USER_ID, "alopez", "ana@smartkin.test", "Ana", "M", "Lopez", "Ana L", "ref",
                UUID.randomUUID(), "+15550100", Set.of(), LocalDate.of(2010, 5, 20), now, now, true, false, false, true,
                ROLE_ID, APPLICATION_ID);
        var backboneCreate = new BackboneUserCreateRequest(USER_ID, "alopez", "pwd", "ana@smartkin.test", true,
                person("Phone"), ROLE_ID, APPLICATION_ID, "Ana", true, true, false);
        var message = new MercuryMessageRequest("email", USER_ID, "ana@smartkin.test", UUID.randomUUID(), APPLICATION_ID, "es",
                "from@smartkin.test", "verification", now, Map.of("vc", "1234-5678"));

        assertTrue(new ConfirmCodeRequest(USER_ID, "1234-5678").toString().contains("1234-5678"));
        assertTrue(userCreateRequest("Lopez", "+15550100").toString().contains("ana@smartkin.test"));
        assertTrue(createResponse.toString().contains("alopez"));
        assertTrue(getResponse.toString().contains("alopez"));
        assertTrue(backboneCreate.toString().contains("alopez"));
        assertTrue(backboneUser(person("Phone"), List.of(), List.of()).toString().contains("alopez"));
        assertTrue(new VerificationCodeRequest(APPLICATION_ID, USER_ID, "1234-5678").toString().contains("1234-5678"));
        assertTrue(message.toString().contains("templateDefinedId"));
        assertFalse(message.toString().contains("ana@smartkin.test"), "the address stays out of the logs");
        assertFalse(message.toString().contains("1234-5678"), "the code stays out of the logs");
        assertTrue(new MercuryMessageRequest("email", USER_ID, "a", null, null, "es", "f", "b", now, null).toString().contains("params=0"));
    }

    @Test
    void updateRequestConvenienceConstructorsFillTheDefaults() {
        var fromList = new BackboneUserUpdateRequest(APPLICATION_ID, true, false, true, List.of(ROLE_ID));
        var fromRole = new BackboneUserUpdateRequest(APPLICATION_ID, true, false, true, ROLE_ID);
        var withoutRole = new BackboneUserUpdateRequest(APPLICATION_ID, true, false, true, (UUID) null);

        assertEquals(List.of(ROLE_ID), fromList.roleIds());
        assertEquals(List.of(ROLE_ID), fromRole.roleIds());
        assertNull(withoutRole.roleIds());
        assertEquals(Boolean.TRUE, fromRole.active());
        assertEquals(APPLICATION_ID, fromRole.application());
    }

    @Test
    void createRequestReadsDatesAndIgnoresUnknownProperties() {
        var json = """
                {"password":"secret","email":"ana@smartkin.test","firstname":"Ana","lastname":"Lopez",
                 "dateOfBirth":"2010-05-20","phoneNumber":"+15550100","unknown":"ignored"}""";

        var request = mapper.readValue(json, UserCreateRequest.class);

        assertEquals(LocalDate.of(2010, 5, 20), request.dateOfBirth());
        assertEquals("Ana", request.firstname());
    }

    @Test
    void mercuryMessageUsesTheContractOfPostApiV1Messages() {
        var sendDate = LocalDateTime.of(2026, 10, 18, 10, 30, 0);
        var message = new MercuryMessageRequest("email", USER_ID, "ana@smartkin.test", UUID.randomUUID(), APPLICATION_ID, "es",
                "from@smartkin.test", "verification", sendDate, Map.of("vc", "1234-5678"));

        var json = mapper.writeValueAsString(message);

        assertTrue(json.contains("\"templateDefinedId\""));
        assertTrue(json.contains("\"userId\""));
        assertTrue(json.contains("\"target\":\"ana@smartkin.test\""));
        assertTrue(json.contains("\"sendDate\":\"2026-10-18 10:30:00\""));
        assertFalse(json.contains("subject"), "the subject is the template's");
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                mapper.readValue("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"status\":\"OPENED\"}", MercuryMessageResponse.class).id());
    }

    @Test
    void backboneRecordsKeepTheirValues() {
        var now = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        var token = new BackboneTokenRequest("ana@smartkin.test", "pwd", APPLICATION_ID);
        var updated = new BackboneUserUpdateResponse(USER_ID, "alopez", "ana@smartkin.test", "Ana", now, now, true, false, false,
                true, UUID.randomUUID(), ROLE_ID, APPLICATION_ID);

        assertEquals(APPLICATION_ID, token.applicationId());
        assertEquals("alopez", updated.alias());
        assertEquals("images/ana.png", new PostProfileImageResponse("images/ana.png").ref());
    }
}
