package com.flashticket.ticket_service.ticket.service.impl;

import com.flashticket.ticket_service.issuance.repository.IssuanceRepository;
import com.flashticket.ticket_service.mapper.TicketMapper;
import com.flashticket.ticket_service.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {
  private final IssuanceRepository issuanceRepository;
  private final TicketMapper ticketMapper;
}
