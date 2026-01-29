package com.marvel.hospitality.reservationservice.dto;


import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.RoomSegment;
import jakarta.validation.constraints.*;


import java.time.LocalDate;


public record ReservationRequest(
        @NotBlank String customerName,
        @NotBlank String roomNumber,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull @Future LocalDate endDate,
        @NotNull RoomSegment segment,
        @NotNull PaymentMode paymentMode,
        String paymentReference
) {}
