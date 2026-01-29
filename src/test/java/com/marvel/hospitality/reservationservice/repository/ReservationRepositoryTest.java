package com.marvel.hospitality.reservationservice.repository;

import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.enumtype.PaymentMode;
import com.marvel.hospitality.reservationservice.enumtype.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("Should find reservations matching status, payment mode, and date (inclusive)")
    void shouldFindReservationsByCriteria() {
        // Given
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);

        Reservation validPast = Reservation.builder()
                .customerName("Valid Past")
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(LocalDate.of(2023, 12, 31))
                .build();

        Reservation validExact = Reservation.builder()
                .customerName("Valid Exact")
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(cutoffDate)
                .build();

        Reservation invalidStatus = Reservation.builder()
                .customerName("Invalid Status")
                .status(ReservationStatus.CANCELLED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(LocalDate.of(2023, 12, 31))
                .build();

        Reservation invalidDate = Reservation.builder()
                .customerName("Future Date")
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(LocalDate.of(2024, 1, 2))
                .build();

        reservationRepository.saveAll(List.of(validPast, validExact, invalidStatus, invalidDate));

        // When
        List<Reservation> results = reservationRepository
                .findByStatusAndPaymentModeAndStartDateLessThanEqual(
                        ReservationStatus.CONFIRMED,
                        PaymentMode.CREDIT_CARD,
                        cutoffDate
                );

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Reservation::getCustomerName)
                .containsExactlyInAnyOrder("Valid Past", "Valid Exact");
    }
}
