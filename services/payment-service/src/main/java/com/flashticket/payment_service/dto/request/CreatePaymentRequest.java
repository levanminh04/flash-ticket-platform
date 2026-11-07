package com.flashticket.payment_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentRequest(@NotNull UUID orderId, String clientIp) {}
