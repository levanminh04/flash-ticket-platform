package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.SeatInventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {}
