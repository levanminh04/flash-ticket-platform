package com.flashticket.payment_service.dto.response;

import com.flashticket.payment_service.dto.common.WireMoney;
import java.time.Instant;
import java.util.UUID;

public record EventFinancialSummary(
    Integer schemaVersion,
    UUID eventId,
    String organizerSubject,
    Instant asOf,
    Boolean hasCollectedOrders,
    String currency,
    @WireMoney String validGross,
    @WireMoney String validRefunds,
    @WireMoney String feeAmount,
    @WireMoney String netAmount) {}
