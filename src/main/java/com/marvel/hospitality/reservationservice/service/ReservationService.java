package com.marvel.hospitality.reservationservice.service;


import com.marvel.hospitality.reservationservice.dto.ReservationRequest;
import com.marvel.hospitality.reservationservice.dto.ReservationResponse;


public interface ReservationService {
    ReservationResponse createReservation(ReservationRequest request);
    void confirmBankTransferPayment(String reservationId);
}
