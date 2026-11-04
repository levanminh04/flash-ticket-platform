package com.flashticket.payment_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundGatewayRequest(
    UUID refundId,
    UUID attemptId,
    String requestKey,
    String txnRef,
    BigDecimal amount,
    String transactionNo,
    String transactionDate) {}
