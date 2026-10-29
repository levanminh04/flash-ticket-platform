package com.flashticket.payment_service.messaging.repository;

import com.flashticket.payment_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {}
