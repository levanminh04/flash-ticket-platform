package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IssueTickets(
    @NotNull UUID orderId, @NotNull UUID chargeId, @NotNull @Valid PurchaseSnapshot purchase) {}
