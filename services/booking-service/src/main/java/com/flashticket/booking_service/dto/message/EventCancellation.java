package com.flashticket.booking_service.dto.message;

import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import java.time.Instant;
import java.util.UUID;

public record EventCancellation(
    @NotNull UUID eventId,
    @NotNull UUID cancellationId,
    @NotBlank
        @Pattern(regexp = "^[1-9][0-9]{0,18}$")
        @DecimalMax("9223372036854775807")
        String sourceConfigVersion,
    @NotNull Instant cancelledAt,
    @NotBlank String actorSubject,
    @NotNull @Valid SalesConfiguration sales,
    @NotNull @Valid AccessConfiguration access) {
  public record AccessConfiguration(
      @NotNull UUID eventId,
      @NotBlank String organizerSubject,
      @NotNull SalesConfiguration.EventStatus eventStatus,
      @NotNull Instant eventStartsAt,
      @NotNull Instant eventEndsAt,
      @NotBlank
          @Pattern(regexp = "^[1-9][0-9]{0,18}$")
          @DecimalMax("9223372036854775807")
          String sourceConfigVersion) {}
}
