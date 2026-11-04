package com.flashticket.payment_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EventRefundProgressView(
    UUID eventId,
    UUID cancellationId,
    boolean selectionComplete,
    long selectedOrderCount,
    long pendingCount,
    long refundedCount,
    long needsAttentionCount,
    Instant completedAt,
    Instant asOf) {}
