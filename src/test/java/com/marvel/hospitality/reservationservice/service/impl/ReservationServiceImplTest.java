package com.marvel.hospitality.reservationservice.service.impl;


import com.marvel.hospitality.reservationservice.client.CreditCardClient;
import com.marvel.hospitality.reservationservice.dto.*;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.*;
import com.marvel.hospitality.reservationservice.exception.*;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.time.LocalDate;
import java.util.Optional;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationRepository repository;
    @Mock
    private CreditCardClient creditCardClient;

    @InjectMocks
    private ReservationServiceImpl service;


    @Test
    void createReservation_Cash_Success() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = service.createReservation(req);

        assertThat(resp.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(repository).save(any());
        verifyNoInteractions(creditCardClient);
    }

    @Test
    void createReservation_CreditCard_Success() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, "REF-123");

        when(creditCardClient.verifyPayment("REF-123"))
                .thenReturn(new PaymentStatusResponse("", PaymentConfirmationStatus.CONFIRMED));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = service.createReservation(req);

        assertThat(resp.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(creditCardClient).verifyPayment("REF-123");
    }

    @Test
    void createReservation_CreditCard_BlankRef_Throws() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, "  ");

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("paymentReference is required for CreditCard payments");
    }

    @Test
    void createReservation_CreditCard_MissingRef_Throws() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, null);

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("paymentReference is required for CreditCard payments");
    }

    @Test
    void createReservation_CreditCard_Rejected_Throws() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, "REF-FAIL");

        when(creditCardClient.verifyPayment("REF-FAIL"))
                .thenReturn(new PaymentStatusResponse("", PaymentConfirmationStatus.REJECTED));

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(PaymentRejectedException.class);
    }

    @Test
    void createReservation_InvalidDates_EndBeforeStart_Throws() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now().plusDays(5), LocalDate.now(),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("Reservation End date must be after Start date");
    }

    @Test
    void createReservation_InvalidDates_TooLong_Throws() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(32),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("The Max reservation duration is 30 days");
    }

    // --- TESTS CONFIRM BANK TRANSFER ---

    @Test
    void confirmBankTransferPayment_NotFound_DoesNothing() {
        when(repository.findById("NONE")).thenReturn(Optional.empty());

        service.confirmBankTransferPayment("NONE");

        verify(repository, never()).save(any());
    }

    @Test
    void confirmBankTransferPayment_WrongMode_DoesNothing() {
        Reservation res = Reservation.builder().status(ReservationStatus.PENDING_PAYMENT)
                .paymentMode(PaymentMode.CASH).build();
        when(repository.findById("ID1")).thenReturn(Optional.of(res));

        service.confirmBankTransferPayment("ID1");

        verify(repository, never()).save(any());
    }

    @Test
    void confirmBankTransferPayment_Success() {
        Reservation res = Reservation.builder()
                .status(ReservationStatus.PENDING_PAYMENT)
                .paymentMode(PaymentMode.BANK_TRANSFER).build();
        when(repository.findById("ID1")).thenReturn(Optional.of(res));

        service.confirmBankTransferPayment("ID1");

        assertThat(res.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(repository).save(res);
    }
}