package com.marvel.hospitality.reservationservice.controller;

import com.marvel.hospitality.reservationservice.dto.ReservationResponse;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    @Test
    void createReservation_success() throws Exception {
        when(service.createReservation(any()))
                .thenReturn(new ReservationResponse("ID123", ReservationStatus.CONFIRMED));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "customerName":"Seif",
                    "roomNumber":"101",
                    "startDate":"2100-02-01",
                    "endDate":"2100-02-05",
                    "segment":"MEDIUM",
                    "paymentMode":"CASH"
                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("ID123"));
    }

    @Test
    void createReservation_invalid_date() throws Exception {
        when(service.createReservation(any()))
                .thenReturn(new ReservationResponse("ID123", ReservationStatus.CONFIRMED));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "customerName":"Seif",
                    "roomNumber":"101",
                    "startDate":"2025-02-01",
                    "endDate":"2026-02-05",
                    "segment":"MEDIUM",
                    "paymentMode":"CASH"
                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createReservation_invalid_dateexce() throws Exception {
        when(service.createReservation(any()))
                .thenThrow(new RuntimeException("UnexpectedException"));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "customerName":"Seif",
                    "roomNumber":"101",
                    "startDate":"2100-02-01",
                    "endDate":"2100-02-05",
                    "segment":"MEDIUM",
                    "paymentMode":"CASH"
                }"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500));
    }
}