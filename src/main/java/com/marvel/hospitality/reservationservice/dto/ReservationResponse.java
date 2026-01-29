package com.marvel.hospitality.reservationservice.dto;


import com.marvel.hospitality.reservationservice.enumtype.ReservationStatus;


public record ReservationResponse(String reservationId, ReservationStatus status) {}
