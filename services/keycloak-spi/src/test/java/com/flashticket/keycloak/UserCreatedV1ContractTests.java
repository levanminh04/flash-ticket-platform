package com.flashticket.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.keycloak.util.JsonSerialization;

class UserCreatedV1ContractTests {
  private String fixture() throws Exception {
    return Files.readString(Path.of("../../docs/contracts/identity/user-created-v1.example.json"));
  }

  @Test void readsSharedFixtureAndPreservesNamesAndUtcTimestamp() throws Exception {
    UserCreatedV1 event = JsonSerialization.readValue(fixture(), UserCreatedV1.class);
    UserCreatedV1 rebuilt = UserCreatedV1.builder()
        .schemaVersion(event.getSchemaVersion())
        .eventId(event.getEventId())
        .eventType(event.getEventType())
        .occurredAt(event.getOccurredAt())
        .realmId(event.getRealmId())
        .identitySubject(event.getIdentitySubject())
        .displayName(event.getDisplayName())
        .build();
    String json = JsonSerialization.writeValueAsString(rebuilt);
    assertEquals(JsonSerialization.writeValueAsString(event), json);
    assertEquals(JsonSerialization.readValue(fixture(), java.util.Map.class),
        JsonSerialization.readValue(json, java.util.Map.class));
    assertTrue(json.contains("\"identitySubject\":\"user-id-from-keycloak\""));
    assertTrue(json.contains("2026-10-26T12:00:00Z"));
    assertFalse(json.contains("password"));
  }

  @Test void rejectsMissingSubjectTypoAndFutureVersion() throws Exception {
    Class<?> type = Class.forName("com.flashticket.keycloak.UserCreatedV1");
    String json = fixture();
    assertThrows(Exception.class, () -> JsonSerialization.readValue(json.replace("identitySubject", "identitySubjet"), type));
    assertThrows(Exception.class, () -> JsonSerialization.readValue(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"), type));
    assertThrows(Exception.class, () -> JsonSerialization.readValue(json.replace("identity.user-created.v1", "identity.user-created.v2"), type));
    UserCreatedV1 event = JsonSerialization.readValue(json, UserCreatedV1.class);
    assertThrows(IllegalArgumentException.class, () -> UserCreatedV1.builder()
        .schemaVersion(event.getSchemaVersion())
        .eventId(event.getEventId())
        .eventType(event.getEventType())
        .occurredAt(event.getOccurredAt())
        .realmId(event.getRealmId())
        .build());
  }

  @Test void omitsAbsentDisplayName() throws Exception {
    Class<?> type = Class.forName("com.flashticket.keycloak.UserCreatedV1");
    String json = fixture().replace(",\n  \"displayName\": \"Nguyễn Văn An\"", "");
    String encoded = JsonSerialization.writeValueAsString(JsonSerialization.readValue(json, type));
    assertFalse(encoded.contains("displayName"));
  }
}
