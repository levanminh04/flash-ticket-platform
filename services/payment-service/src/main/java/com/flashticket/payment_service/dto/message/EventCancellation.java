package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.validation.ContractRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class EventCancellation {
  @NotNull private final UUID eventId;
  @NotNull private final UUID cancellationId;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceConfigVersion;

  @NotNull private final Instant cancelledAt;
  @NotBlank private final String actorSubject;
  @NotNull @Valid private final SalesConfiguration sales;
  @NotNull @Valid private final AccessConfiguration access;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public EventCancellation(
      @JsonProperty(value = "eventId", required = true) UUID eventId,
      @JsonProperty(value = "cancellationId", required = true) UUID cancellationId,
      @JsonProperty(value = "sourceConfigVersion", required = true) String sourceConfigVersion,
      @JsonProperty(value = "cancelledAt", required = true) Instant cancelledAt,
      @JsonProperty(value = "actorSubject", required = true) String actorSubject,
      @JsonProperty(value = "sales", required = true) SalesConfiguration sales,
      @JsonProperty(value = "access", required = true)
          EventCancellation.AccessConfiguration access) {
    this.eventId = eventId;
    this.cancellationId = cancellationId;
    this.sourceConfigVersion = sourceConfigVersion;
    this.cancelledAt = cancelledAt;
    this.actorSubject = actorSubject;
    this.sales = sales;
    this.access = access;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidCancellation() {
    return ContractRules.validCancellation(this);
  }

  @Getter
  public static final class AccessConfiguration {
    @NotNull private final UUID eventId;
    @NotBlank private final String organizerSubject;
    @NotNull private final EventState eventStatus;
    @NotNull private final Instant eventStartsAt;
    @NotNull private final Instant eventEndsAt;

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    @DecimalMax("9223372036854775807")
    private final String sourceConfigVersion;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public AccessConfiguration(
        @JsonProperty(value = "eventId", required = true) UUID eventId,
        @JsonProperty(value = "organizerSubject", required = true) String organizerSubject,
        @JsonProperty(value = "eventStatus", required = true) EventState eventStatus,
        @JsonProperty(value = "eventStartsAt", required = true) Instant eventStartsAt,
        @JsonProperty(value = "eventEndsAt", required = true) Instant eventEndsAt,
        @JsonProperty(value = "sourceConfigVersion", required = true) String sourceConfigVersion) {
      this.eventId = eventId;
      this.organizerSubject = organizerSubject;
      this.eventStatus = eventStatus;
      this.eventStartsAt = eventStartsAt;
      this.eventEndsAt = eventEndsAt;
      this.sourceConfigVersion = sourceConfigVersion;
    }
  }
}
