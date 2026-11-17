package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.controller.FamilyContractApi;
import com.umdc.smartkin.api.v1.controller.FamilyContractController;
import com.umdc.smartkin.api.v1.to.FamilyContractRequest;
import com.umdc.smartkin.mongo.document.FamilyContractDocument;
import com.umdc.smartkin.mongo.repository.FamilyContractRepository;
import com.umdc.smartkin.security.access.AccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyContractServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Mock
    private FamilyContractRepository repository;
    @Mock
    private AccessPolicy accessPolicy;
    private FamilyContractServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamilyContractServiceImpl(repository, accessPolicy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FamilyContractRequest request(LocalDate date) {
        return new FamilyContractRequest(" Lopez ", List.of(" No datos personales ", "Respeto"), " Ana ", date);
    }

    @Test
    void savesTheContractOfTheParentWithTrimmedTextAndTodayAsTheDefaultDate() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.put(tokenFor(PARENT_ID), request(null));

        var saved = ArgumentCaptor.forClass(FamilyContractDocument.class);
        verify(repository).save(saved.capture());
        assertEquals(PARENT_ID, saved.getValue().parentId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Lopez", response.getBody().familyName());
        assertEquals(List.of("No datos personales", "Respeto"), response.getBody().rules());
        assertEquals("Ana", response.getBody().signature());
        assertEquals(LocalDate.of(2026, 11, 17), response.getBody().agreementDate());
        assertEquals(NOW, response.getBody().updatedAt());
    }

    @Test
    void keepsTheDateTheParentChose() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.put(tokenFor(PARENT_ID), request(LocalDate.of(2026, 1, 2)));

        assertEquals(LocalDate.of(2026, 1, 2), response.getBody().agreementDate());
    }

    @Test
    void readsTheContractOfTheCallerOnly() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(repository.findById(PARENT_ID)).thenReturn(Optional.of(
                new FamilyContractDocument(PARENT_ID, "Lopez", List.of("Respeto"), "Ana", LocalDate.of(2026, 1, 2), NOW)));

        var response = service.get(tokenFor(PARENT_ID));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Lopez", response.getBody().familyName());
    }

    @Test
    void answers404WhenThereIsNoContractYet() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(repository.findById(PARENT_ID)).thenReturn(Optional.empty());

        var response = service.get(tokenFor(PARENT_ID));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("There is no family contract yet.", response.getHeaders().getFirst("message-error"));
    }

    @Test
    void refusesWhoIsNotAParentAndTokensWithoutAUser() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, service.get(tokenFor(PARENT_ID)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.put(tokenFor(PARENT_ID), request(null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.get("garbage").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.put("garbage", request(null)).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void answers503WhenMongoFails() {
        when(accessPolicy.isParent(PARENT_ID)).thenReturn(true);
        when(repository.findById(PARENT_ID)).thenThrow(new DataAccessResourceFailureException("down"));
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("down"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, service.get(tokenFor(PARENT_ID)).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, service.put(tokenFor(PARENT_ID), request(null)).getStatusCode());
    }

    @Test
    void theControllerDelegatesAndTheDefaultsAnswerNotImplemented() {
        var delegate = mock(FamilyContractService.class);
        var controller = new FamilyContractController(delegate);
        var body = request(null);
        when(delegate.get("t")).thenReturn(org.springframework.http.ResponseEntity.ok().build());
        when(delegate.put("t", body)).thenReturn(org.springframework.http.ResponseEntity.ok().build());

        assertEquals(HttpStatus.OK, controller.getFamilyContract("t").getStatusCode());
        assertEquals(HttpStatus.OK, controller.putFamilyContract("t", body).getStatusCode());
        assertSame(delegate, controller.getService());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, new FamilyContractApi() { }.getFamilyContract("t").getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, new FamilyContractApi() { }.putFamilyContract("t", body).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, new FamilyContractService() { }.get("t").getStatusCode());
    }
}
