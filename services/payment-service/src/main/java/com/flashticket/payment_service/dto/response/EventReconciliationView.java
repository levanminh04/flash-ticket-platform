package com.flashticket.payment_service.dto.response;

import com.flashticket.payment_service.dto.common.WireMoney;
import java.time.Instant;
import java.util.UUID;

public record EventReconciliationView(
    UUID eventId,
    String organizerSubject,
    String currency,
    @WireMoney String validGross,
    @WireMoney String validRefunds,
    @WireMoney String feeAmount,
    @WireMoney String netAmount,
    String feeRate,
    Long sourceFeeVersion,
    Instant eventEndsAt,
    boolean cancelled,
    long paymentPendingCount,
    long refundPendingCount,
    boolean eligibleForReconciliation,
    String reconciledBy,
    Instant reconciledAt,
    String reconciliationEvidence,
    String paidBy,
    Instant paidAt,
    Instant asOf) {}
