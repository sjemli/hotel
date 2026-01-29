package com.marvel.hospitality.reservationservice.dto;


import com.marvel.hospitality.reservationservice.model.ReservationStatus;


public record ReservationResponse(String reservationId, ReservationStatus status) {}
