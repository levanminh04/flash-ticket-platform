package com.flashticket.payment_service.payment.controller;

import com.flashticket.payment_service.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService paymentService;
}
