package com.marvel.hospitality.reservationservice.service.impl;


import com.marvel.hospitality.reservationservice.client.CreditCardClient;
import com.marvel.hospitality.reservationservice.dto.*;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.exception.*;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static com.marvel.hospitality.reservationservice.model.PaymentMode.CASH;
import static com.marvel.hospitality.reservationservice.model.ReservationStatus.CONFIRMED;
import static com.marvel.hospitality.reservationservice.model.ReservationStatus.PENDING_PAYMENT;


@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository repository;
    private final CreditCardClient creditCardClient;

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        validateDates(request.startDate(), request.endDate());


        Reservation reservation = Reservation.builder()
                .customerName(request.customerName())
                .roomNumber(request.roomNumber())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .segment(request.segment())
                .paymentMode(request.paymentMode())
                .paymentReference(request.paymentReference())
                .status(request.paymentMode() == CASH ? CONFIRMED : PENDING_PAYMENT)
                .build();


        if (request.paymentMode() == PaymentMode.CREDIT_CARD) {
            handleCreditCardPayment(reservation, request.paymentReference());
        }


        repository.save(reservation);
        log.info("Created reservation {}", reservation.getId());


        return new ReservationResponse(reservation.getId(), reservation.getStatus());
    }


    private void validateDates(LocalDate start, LocalDate end) {
        if (!end.isAfter(start)) throw new ReservationValidationException("End date must be after start");
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > 30) throw new ReservationValidationException("Max 30 days");
    }


    private void handleCreditCardPayment(Reservation res, String ref) {
        if (ref == null || ref.isBlank()) throw new ReservationValidationException("Ref missing");

        var response = creditCardClient.verifyPayment(ref);
        if (response.status() != PaymentConfirmationStatus.CONFIRMED) {
            throw new PaymentRejectedException("Status: " + response.status());
        }
        res.setStatus(ReservationStatus.CONFIRMED);
    }


    @Transactional
    public void confirmBankTransferPayment(String reservationId) {
        Reservation res = repository.findById(reservationId).orElse(null);
        if (res == null) {
            log.warn("Reservation {} not found - skipping", reservationId);
            return;
        }
        if (res.getStatus() == PENDING_PAYMENT &&
            res.getPaymentMode() == PaymentMode.BANK_TRANSFER) {
            res.setStatus(CONFIRMED);
            repository.save(res);
            log.info("Confirmed {}", reservationId);
        } else {
            log.info("Skipped {} (already {})", reservationId, res.getStatus());
        }
    }
}
