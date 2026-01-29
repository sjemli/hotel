package com.marvel.hospitality.reservationservice.controller;


import com.marvel.hospitality.reservationservice.dto.ReservationRequest;
import com.marvel.hospitality.reservationservice.dto.ReservationResponse;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {


    private final ReservationService service;


    @Operation(summary = "Confirm room reservation")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationRequest request) {
        return service.createReservation(request);
    }
}
