package com.flashticket.ticket_service.delivery.controller;

import com.flashticket.ticket_service.delivery.service.TicketDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class TicketDeliveryController {
  private final TicketDeliveryService ticketDeliveryService;
}
