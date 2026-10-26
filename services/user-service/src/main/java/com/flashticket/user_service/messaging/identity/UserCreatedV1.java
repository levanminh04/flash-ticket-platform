package com.flashticket.user_service.messaging.identity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserCreatedV1(
    int schemaVersion,
    UUID eventId,
    String eventType,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
    String realmId,
    String identitySubject,
    String displayName) {
  public static final String TYPE = "identity.user-created.v1";

  public UserCreatedV1 {
    if (schemaVersion != 1 || !TYPE.equals(eventType))
      throw new IllegalArgumentException("Unsupported identity event");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (realmId == null
        || realmId.isBlank()
        || identitySubject == null
        || identitySubject.isBlank()) throw new IllegalArgumentException("Missing identity");
    if (displayName != null && displayName.isBlank())
      throw new IllegalArgumentException("Blank displayName");
  }
}
