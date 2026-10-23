package com.flashticket.booking_service.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class CreateOrderRequest {
  @NotNull private final UUID eventId;
  @NotBlank @Email @Size(max = 254) private final String customerEmail;
  @NotNull @Valid private final OrderSelection selection;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public CreateOrderRequest(
      @JsonProperty(value = "eventId", required = true) UUID eventId,
      @JsonProperty(value = "customerEmail", required = true) String customerEmail,
      @JsonProperty(value = "selection", required = true) OrderSelection selection) {
    this.eventId = eventId;
    this.customerEmail = customerEmail;
    this.selection = selection;
  }
}
