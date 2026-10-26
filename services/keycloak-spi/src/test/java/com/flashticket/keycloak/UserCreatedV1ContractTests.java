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
    Class<?> type = Class.forName("com.flashticket.keycloak.UserCreatedV1");
    Object event = JsonSerialization.readValue(fixture(), type);
    String json = JsonSerialization.writeValueAsString(event);
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
  }

  @Test void omitsAbsentDisplayName() throws Exception {
    Class<?> type = Class.forName("com.flashticket.keycloak.UserCreatedV1");
    String json = fixture().replace(",\n  \"displayName\": \"Nguyễn Văn An\"", "");
    String encoded = JsonSerialization.writeValueAsString(JsonSerialization.readValue(json, type));
    assertFalse(encoded.contains("displayName"));
  }
}
