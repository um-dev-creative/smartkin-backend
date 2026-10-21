package com.umdc.smartkin.support;

import com.umdc.commons.general.pojo.Application;
import com.umdc.commons.general.pojo.Contact;
import com.umdc.commons.general.pojo.ContactType;
import com.umdc.commons.general.pojo.Person;
import com.umdc.commons.general.pojo.Role;
import com.umdc.smartkin.api.v1.to.UserCreateRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserGetResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Shared builders for the unit tests.
public final class TestData {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID APPLICATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID PHONE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    public static final UUID PARENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    public static final UUID CHILD_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private TestData() {
    }

    /// Builds the FeignException subclass that Feign would raise for the given status.
    public static FeignException feign(int status) {
        return feign(status, Map.of());
    }

    public static FeignException feign(int status, Map<String, Collection<String>> headers) {
        var request = Request.create(Request.HttpMethod.GET, "/test", Map.of(), null, StandardCharsets.UTF_8, null);
        var response = Response.builder().status(status).reason("test").request(request).headers(headers).build();
        return FeignException.errorStatus("test", response);
    }

    public static UserCreateRequest userCreateRequest(String lastname, String phone) {
        return new UserCreateRequest("secret", "ana@smartkin.test", "Ana", lastname, LocalDate.of(2010, 5, 20),
                phone, "Ana", true, true, false);
    }

    public static Person person(String contactTypeName) {
        var type = new ContactType();
        type.setName(contactTypeName);
        var contact = new Contact();
        contact.setId(PHONE_ID);
        contact.setContent("+15550100");
        contact.setContactType(type);
        var person = new Person();
        person.setFirstName("Ana");
        person.setMiddleName("M");
        person.setLastName("Lopez");
        person.setBirthdate(LocalDate.of(2010, 5, 20));
        person.setContacts(List.of(contact));
        return person;
    }

    public static Role role(UUID id) {
        var role = new Role();
        role.setId(id);
        return role;
    }

    public static Application application(UUID id) {
        var application = new Application();
        application.setId(id);
        return application;
    }

    public static BackboneUserGetResponse backboneUser(Person person, List<Role> roles, List<Application> applications) {
        return new BackboneUserGetResponse(USER_ID, "alopez", "pwd", "ana@smartkin.test", "Ana",
                LocalDateTime.of(2026, 1, 2, 3, 4, 5), LocalDateTime.of(2026, 2, 3, 4, 5, 6), true, true, false, false,
                person, roles, applications);
    }

    /// Builds an unsigned session token whose payload carries the given `uid` claim (what `JwtUtil` reads).
    public static String tokenFor(UUID uid) {
        var payload = "{\"uid\":\"" + uid + "\"}";
        return "header." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
