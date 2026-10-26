package com.flashticket.ticket_service.messaging.repository;

import com.flashticket.ticket_service.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, InboxMessageId> {}
