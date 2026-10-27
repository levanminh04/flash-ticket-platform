package com.flashticket.keycloak;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Contract JSON dùng giữa SPI và User Service.
 * eventId nhận diện sự kiện khi gửi lại; identitySubject nhận diện user trong Keycloak.
 * schemaVersion/eventType khóa phiên bản; occurredAt là thời điểm đăng ký theo UTC.
 * displayName tùy chọn: khi không có thì bỏ field khỏi JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class UserCreatedV1 {
  public static final String TYPE = "identity.user-created.v1";

  private final int schemaVersion;
  private final UUID eventId;
  private final String eventType;
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private final Instant occurredAt;
  private final String realmId;
  private final String identitySubject;
  private final String displayName;

  // Builder và Jackson đều qua constructor này để không bỏ qua kiểm tra contract.
  @Builder
  @JsonCreator
  public UserCreatedV1(
      @JsonProperty("schemaVersion") int schemaVersion,
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("realmId") String realmId,
      @JsonProperty("identitySubject") String identitySubject,
      @JsonProperty("displayName") String displayName) {
    if (schemaVersion != 1 || !TYPE.equals(eventType))
      throw new IllegalArgumentException("Unsupported identity event");

    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");

    if (realmId == null || realmId.isBlank() || identitySubject == null || identitySubject.isBlank())
      throw new IllegalArgumentException("Missing identity");
    if (displayName != null && displayName.isBlank())
      throw new IllegalArgumentException("Blank displayName");

    this.schemaVersion = schemaVersion;
    this.eventId = eventId;
    this.eventType = eventType;
    this.occurredAt = occurredAt;
    this.realmId = realmId;
    this.identitySubject = identitySubject;
    this.displayName = displayName;
  }
}
