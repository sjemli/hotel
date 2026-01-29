package com.marvel.hospitality.reservationservice.repository;


import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.enumtype.PaymentMode;
import com.marvel.hospitality.reservationservice.enumtype.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDate;
import java.util.List;


public interface ReservationRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findByStatusAndPaymentModeAndStartDateLessThanEqual(
            ReservationStatus status, PaymentMode mode, LocalDate date);
}
