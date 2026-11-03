package com.flashticket.payment_service.dto.response;

import com.flashticket.payment_service.entity.enums.RefundGatewayOutcome;

public record RefundGatewayResult(
    RefundGatewayOutcome outcome, String providerRefundId, String responseCode) {}
