package com.flashticket.ticket_service.issuance.controller;

import com.flashticket.ticket_service.issuance.service.IssuanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderTicketsController {
  private final IssuanceService issuanceService;
}
