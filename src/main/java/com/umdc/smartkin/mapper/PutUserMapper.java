package com.umdc.smartkin.mapper;

import com.prx.commons.services.config.mapper.MapperAppConfig;
import com.umdc.smartkin.api.v1.to.PutUserRequest;
import com.umdc.smartkin.client.backbone.to.BackboneUserUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.PHONE_CONTACT_TYPE_ID;

/**
 * Mapper interface for converting PatchUserRequest to BackboneUserUpdateRequest.
 */
@Mapper(
        // Specifies the configuration class to use for this mapper.
        config = MapperAppConfig.class
)
public interface PutUserMapper {

    @Mapping(target = "application", source = "applicationId")
    @Mapping(target = "active", source = "request.active")
    @Mapping(target = "roleIds", source = "request.roleIds")
    @Mapping(target = "displayName", source = "request.displayName")
    @Mapping(target = "notificationSms", source = "request.notificationSms")
    @Mapping(target = "notificationEmail", source = "request.notificationEmail")
    @Mapping(target = "privacyDataOutActive", source = "request.privacyDataOutActive")
    @Mapping(target = "contacts", expression = "java(mapContacts(request))")
    BackboneUserUpdateRequest toBackbone(UUID applicationId, PutUserRequest request);

    /**
     * Helper method to map person fields from PatchUserRequest to BackboneUserUpdateRequest.Person.
     */
    default List<BackboneUserUpdateRequest.Contact> mapContacts(PutUserRequest request) {
        return List.of(new BackboneUserUpdateRequest.Contact(
                request.phoneId(),
                request.phoneNumber(),
                new BackboneUserUpdateRequest.ContactType(PHONE_CONTACT_TYPE_ID),
                true
        ));
    }
}

