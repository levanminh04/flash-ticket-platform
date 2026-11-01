package com.flashticket.booking_service.messaging.repository;

import com.flashticket.booking_service.entity.OutboxMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {}
