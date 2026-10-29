package com.flashticket.payment_service.finance.controller;

import com.flashticket.payment_service.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/events")
@RequiredArgsConstructor
public class InternalFinanceController {
  private final FinanceService financeService;
}
