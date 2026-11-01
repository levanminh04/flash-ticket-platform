package com.flashticket.keycloak;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Dữ liệu đăng ký trong Envelope.data; metadata nằm ở envelope.
 * displayName tùy chọn: khi không có thì bỏ field khỏi JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class UserCreatedV1 {
  public static final String TYPE = "identity.user-created.v1";

  private final String realmId;
  private final String identitySubject;
  private final String displayName;

  // Builder và Jackson đều qua constructor này để không bỏ qua kiểm tra contract.
  @Builder
  @JsonCreator
  public UserCreatedV1(
      @JsonProperty("realmId") String realmId,
      @JsonProperty("identitySubject") String identitySubject,
      @JsonProperty("displayName") String displayName) {
    if (realmId == null || realmId.isBlank() || identitySubject == null || identitySubject.isBlank())
      throw new IllegalArgumentException("Missing identity");
    if (displayName != null && displayName.isBlank())
      throw new IllegalArgumentException("Blank displayName");

    this.realmId = realmId;
    this.identitySubject = identitySubject;
    this.displayName = displayName;
  }
}
