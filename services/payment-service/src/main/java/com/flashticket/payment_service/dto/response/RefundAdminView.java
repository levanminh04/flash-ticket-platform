package com.flashticket.payment_service.dto.response;

import com.flashticket.payment_service.dto.common.WireMoney;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.entity.enums.RefundState;
import java.time.Instant;
import java.util.UUID;

public record RefundAdminView(
    UUID refundId,
    UUID orderId,
    UUID chargeId,
    @WireMoney String amount,
    String currency,
    RefundReason reason,
    RefundState state,
    Instant createdAt,
    Instant completedAt,
    String settledBy,
    String externalReference,
    Instant lastAttemptStartedAt,
    Instant lastAttemptFinishedAt,
    String lastAttemptOutcome) {}
