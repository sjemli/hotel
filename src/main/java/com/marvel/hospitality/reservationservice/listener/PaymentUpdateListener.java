package com.marvel.hospitality.reservationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentUpdateListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReservationService service;
    private static final Pattern RESERVATION_ID_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @KafkaListener(topics = "bank-transfer-payment-update", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String payload = record.value();
        try {
            String reservationId = getReservationId(payload);
            log.info("Processing valid bank transfer payment update for reservation {}", reservationId);
            service.confirmBankTransferPayment(reservationId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed processing message - will retry / send to DLQ: {}", payload, e);
            throw new RuntimeException("Message processing failed", e);
        }
    }

    private String getReservationId(String payload) throws JsonProcessingException {
        PaymentUpdateEvent event = objectMapper.readValue(payload, PaymentUpdateEvent.class);
        String desc = event.transactionDescription();
        if (desc == null || desc.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing transactionDescription");
        }
        String[] parts = desc.trim().split(" ");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid transactionDescription format - expected E2E<10chars> <reservationId>");
        }
        String reservationId = parts[1].trim();
        if (!RESERVATION_ID_PATTERN.matcher(reservationId).matches()) {
            throw new IllegalArgumentException("Invalid reservationId (must be exactly 8 uppercase alphanumeric): " + reservationId);
        }
        return reservationId;
    }
}
