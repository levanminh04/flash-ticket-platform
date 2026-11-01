package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefundController {
  private final RefundService refundService;
}
