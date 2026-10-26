package com.flashticket.ticket_service.messaging.service.impl;

import com.flashticket.ticket_service.mapper.OutboxMessageMapper;
import com.flashticket.ticket_service.messaging.repository.InboxMessageRepository;
import com.flashticket.ticket_service.messaging.repository.OutboxMessageRepository;
import com.flashticket.ticket_service.messaging.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessagingServiceImpl implements MessagingService {
  private final InboxMessageRepository inboxMessageRepository;
  private final OutboxMessageRepository outboxMessageRepository;
  private final OutboxMessageMapper outboxMessageMapper;
}
