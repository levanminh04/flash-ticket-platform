package com.flashticket.ticket_service.delivery.repository;

import com.flashticket.ticket_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketDeliveryRepository extends JpaRepository<TicketDelivery, UUID> {}
