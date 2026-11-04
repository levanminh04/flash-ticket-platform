package com.flashticket.ticket_service.delivery.service.impl;

import com.flashticket.ticket_service.delivery.repository.TicketDeliveryRepository;
import com.flashticket.ticket_service.delivery.service.TicketDeliveryService;
import com.flashticket.ticket_service.issuance.repository.IssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketDeliveryServiceImpl implements TicketDeliveryService {
  private final TicketDeliveryRepository ticketDeliveryRepository;
  private final IssuanceRepository issuanceRepository;
}
