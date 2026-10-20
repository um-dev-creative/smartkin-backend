package com.umdc.smartkin.kafka.producer;

import com.umdc.commons.constants.httpstatus.type.MessageType;
import com.umdc.commons.exception.StandardException;
import com.umdc.smartkin.kafka.to.EmailMessageTO;
import com.umdc.smartkin.kafka.to.Recipient;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailMessageProducerServiceImplTest {

    private static final String TOPIC = "email-topic";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private EmailMessageProducerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailMessageProducerServiceImpl(kafkaTemplate, JsonMapper.builder().build());
        ReflectionTestUtils.setField(service, "topic", TOPIC);
    }

    private static EmailMessageTO message() {
        return new EmailMessageTO(UUID.randomUUID(), UUID.randomUUID(), "noreply@smartkin.test",
                List.of(new Recipient("Ana", "ana@smartkin.test", "ana")), List.of(), "Subject", "Body",
                LocalDateTime.of(2026, 10, 18, 10, 30, 0), Map.of("code", "123456"));
    }

    @Test
    void sendMessagePublishesJsonWithSnakeCaseFieldsToTopic() {
        var sendResult = mock(SendResult.class);
        var metadata = mock(RecordMetadata.class);
        when(metadata.offset()).thenReturn(7L);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(eq(TOPIC), any(String.class))).thenReturn(CompletableFuture.completedFuture(sendResult));

        assertDoesNotThrow(() -> service.sendMessage(message()));

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), payload.capture());
        assertTrue(payload.getValue().contains("\"template_defined_id\""));
        assertTrue(payload.getValue().contains("\"user_id\""));
        assertTrue(payload.getValue().contains("\"send_date\""));
    }

    @Test
    void sendMessageLogsAndKeepsGoingWhenKafkaSendFails() {
        when(kafkaTemplate.send(eq(TOPIC), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertDoesNotThrow(() -> service.sendMessage(message()));

        verify(kafkaTemplate).send(eq(TOPIC), any(String.class));
    }

    @Test
    void sendMessageWrapsSerializationErrorsInStandardException() {
        var failingMapper = mock(ObjectMapper.class);
        var cause = new JacksonException("boom") { };
        when(failingMapper.writeValueAsString(any())).thenThrow(cause);
        var failing = new EmailMessageProducerServiceImpl(kafkaTemplate, failingMapper);
        ReflectionTestUtils.setField(failing, "topic", TOPIC);

        var ex = assertThrows(StandardException.class, () -> failing.sendMessage(message()));

        assertSame(cause, ex.getCause());
        assertEquals(MessageType.DEFAULT_MESSAGE, ex.getStatus());
        verifyNoInteractions(kafkaTemplate);
    }
}
