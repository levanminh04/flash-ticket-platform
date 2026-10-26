package com.flashticket.ticket_service.messaging.repository;

import com.flashticket.ticket_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {}
