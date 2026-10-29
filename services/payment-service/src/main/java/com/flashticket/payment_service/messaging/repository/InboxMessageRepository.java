package com.flashticket.payment_service.messaging.repository;

import com.flashticket.payment_service.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, InboxMessageId> {}
