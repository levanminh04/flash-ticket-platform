package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.TicketTypeInventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeInventoryRepository extends JpaRepository<TicketTypeInventory, UUID> {}
