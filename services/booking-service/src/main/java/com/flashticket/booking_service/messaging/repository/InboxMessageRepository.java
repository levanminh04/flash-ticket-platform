package com.flashticket.booking_service.messaging.repository;

import com.flashticket.booking_service.entity.InboxMessage;
import com.flashticket.booking_service.entity.InboxMessageId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, InboxMessageId> {}
