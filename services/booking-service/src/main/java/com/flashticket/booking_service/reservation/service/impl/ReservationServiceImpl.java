package com.flashticket.booking_service.reservation.service.impl;

import com.flashticket.booking_service.reservation.repository.ReservationRepository;
import com.flashticket.booking_service.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {
  private final ReservationRepository reservationRepository;
}
