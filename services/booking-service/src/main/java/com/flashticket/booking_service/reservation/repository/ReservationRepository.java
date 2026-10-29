package com.flashticket.booking_service.reservation.repository;

import com.flashticket.booking_service.entity.Reservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
  Optional<Reservation> findByOrderId(UUID orderId);
}
