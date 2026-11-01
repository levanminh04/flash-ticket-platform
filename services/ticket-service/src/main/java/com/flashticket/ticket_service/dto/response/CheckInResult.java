package com.flashticket.ticket_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record CheckInResult(
    @NotNull UUID attemptId,
    @NotNull UUID eventId,
    @JsonProperty(required = true) UUID ticketId,
    @NotNull Result result,
    boolean succeeded,
    @NotNull Instant processedAt) {
  public enum Result {
    SUCCESS,
    NOT_EVENT_OWNER,
    OUTSIDE_WINDOW,
    UNKNOWN_QR,
    WRONG_EVENT,
    NOT_ISSUED,
    VOID,
    ALREADY_USED,
    EVENT_CANCELLED
  }

  @AssertTrue
  @JsonIgnore
  public boolean isConsistent() {
    return result != null
        && succeeded == (result == Result.SUCCESS)
        && (!succeeded || ticketId != null);
  }
}
