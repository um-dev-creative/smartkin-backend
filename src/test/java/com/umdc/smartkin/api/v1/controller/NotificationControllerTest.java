package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.NotificationService;
import com.umdc.smartkin.api.v1.to.NotificationResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.UnreadCountResponse;
import com.umdc.smartkin.jpa.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String TOKEN = "session-token-value";
    private static final UUID ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    private NotificationService service;
    private NotificationController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static NotificationResponse notification() {
        return new NotificationResponse(ID, NotificationType.ACHIEVEMENT, "¡Nuevo logro!", "Conseguiste el logro", null,
                Instant.parse("2026-10-21T12:00:00Z"), false, null);
    }

    @Test
    void delegatesEveryOperationToTheService() {
        ResponseEntity<PageResponse<NotificationResponse>> page = ResponseEntity.ok(new PageResponse<>(0, 20, 0, 0, List.of()));
        ResponseEntity<UnreadCountResponse> count = ResponseEntity.ok(new UnreadCountResponse(2));
        ResponseEntity<Void> done = ResponseEntity.noContent().build();
        when(service.list(TOKEN, true, 1, 5)).thenReturn(page);
        when(service.unreadCount(TOKEN)).thenReturn(count);
        when(service.markAllRead(TOKEN)).thenReturn(done);
        when(service.markRead(TOKEN, ID)).thenReturn(done);

        assertSame(page, controller.listNotifications(TOKEN, true, 1, 5));
        assertSame(count, controller.getUnreadNotificationCount(TOKEN));
        assertSame(done, controller.markAllNotificationsRead(TOKEN));
        assertSame(done, controller.markNotificationRead(TOKEN, ID));
        assertSame(service, controller.getService());
    }

    @Test
    void theApiAnswers501UntilAServiceIsProvided() {
        var api = new NotificationApi() {};

        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.listNotifications(TOKEN, false, 0, 20).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.getUnreadNotificationCount(TOKEN).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.markAllNotificationsRead(TOKEN).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, api.markNotificationRead(TOKEN, ID).getStatusCode());
    }

    @Test
    void theHttpLayerRoutesEachPathToItsOperation() throws Exception {
        when(service.list(TOKEN, false, 0, 20)).thenReturn(ResponseEntity.ok(new PageResponse<>(0, 20, 1, 1, List.of(notification()))));
        when(service.unreadCount(TOKEN)).thenReturn(ResponseEntity.ok(new UnreadCountResponse(7)));
        when(service.markAllRead(TOKEN)).thenReturn(ResponseEntity.noContent().build());
        when(service.markRead(TOKEN, ID)).thenReturn(ResponseEntity.noContent().build());

        mvc.perform(get("/api/v1/notifications").header("session-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("ACHIEVEMENT"))
                .andExpect(jsonPath("$.content[0].read").value(false));
        mvc.perform(get("/api/v1/notifications/unread-count").header("session-token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(7));
        mvc.perform(post("/api/v1/notifications/read-all").header("session-token", TOKEN)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/notifications/{id}/read", ID).header("session-token", TOKEN)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/notifications/not-a-uuid/read").header("session-token", TOKEN)).andExpect(status().isBadRequest());
        verify(service).markAllRead(TOKEN);
        verify(service).markRead(TOKEN, ID);
    }
}
