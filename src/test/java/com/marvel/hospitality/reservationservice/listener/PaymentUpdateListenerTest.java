package com.marvel.hospitality.reservationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentUpdateListenerTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private Acknowledgment acknowledgment;

    private PaymentUpdateListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new PaymentUpdateListener(reservationService);
    }

    @Test
    void onMessage_WithValidId_CallsServiceAndAcknowledges() throws Exception {
        // Arrange
        String validId = "CONF1234";
        String payload = createPayload("E2E-REF " + validId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, null, payload);

        // Act
        listener.onMessage(record, acknowledgment);

        // Assert
        verify(reservationService).confirmBankTransferPayment(validId);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onMessage_WithLowercaseId_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        String invalidId = "conf1234"; // lowercase not allowed by pattern
        String payload = createPayload("E2E-REF " + invalidId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, null, payload);

        // Act & Assert
        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class) 
                .hasStackTraceContaining("IllegalArgumentException")
                .hasMessageContaining("Invalid reservationId");

        verifyNoInteractions(reservationService);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void onMessage_WithMalformedDescription_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        String payload = createPayload("OnlyOnePart");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, null, payload);

        // Act & Assert
        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .hasMessageContaining("Invalid transactionDescription format");
    }

    private String createPayload(String desc) throws Exception {
        PaymentUpdateEvent event = new PaymentUpdateEvent("TXN123", "ACC1", BigDecimal.TEN, desc);
        return objectMapper.writeValueAsString(event);
    }
}
