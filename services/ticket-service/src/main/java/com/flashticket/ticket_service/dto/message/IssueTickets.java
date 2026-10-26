package com.flashticket.ticket_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flashticket.ticket_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record IssueTickets(
    @NotNull UUID orderId, @NotNull UUID chargeId, @NotNull @Valid PurchaseSnapshot purchase) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidInput() {
    return com.flashticket.ticket_service.validation.ContractRules.validIssueTickets(this);
  }
}
