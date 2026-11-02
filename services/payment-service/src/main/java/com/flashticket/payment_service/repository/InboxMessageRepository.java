package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.InboxMessage;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, InboxMessageId> {}
