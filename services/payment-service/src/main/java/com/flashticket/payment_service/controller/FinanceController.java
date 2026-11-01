package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FinanceController {
  private final FinanceService financeService;
}
