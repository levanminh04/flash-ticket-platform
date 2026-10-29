package com.flashticket.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keycloak.util.JsonSerialization;

class UserCreatedV1ContractTests {
  private String fixture() throws Exception {
    return Files.readString(Path.of("../../docs/contracts/identity/user-created-envelope-v1.example.json"));
  }

  @Test void readsSharedFixtureAndPreservesNamesAndUtcTimestamp() throws Exception {
    Envelope<UserCreatedV1> event = JsonSerialization.mapper.readValue(fixture(), JsonSerialization.mapper.getTypeFactory().constructParametricType(Envelope.class, UserCreatedV1.class));
    String json = JsonSerialization.writeValueAsString(event);
    assertEquals(JsonSerialization.readValue(fixture(), Map.class), JsonSerialization.readValue(json, Map.class));
    assertEquals(event.getAggregateId().toString(), event.getData().getIdentitySubject());
    assertEquals(event.getMessageId(), event.getCorrelationId());
    assertEquals("1", event.getAggregateVersion());
    assertTrue(json.contains("2026-10-26T12:00:00Z"));
    assertTrue(json.contains("\"causationId\":null"));
    assertTrue(json.contains("\"traceparent\":null"));
    assertFalse(json.contains("password"));
  }

  @Test void rejectsMissingSubjectTypoAndFutureVersion() throws Exception {
    String json = fixture();
    for (String invalid : new String[] {
        json.replace("identitySubject", "identitySubjet"),
        json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
        json.replace("\"aggregateVersion\": \"1\"", "\"aggregateVersion\": \"0\""),
        json.replace("\"aggregateVersion\": \"1\"", "\"aggregateVersion\": \"9223372036854775808\""),
        json.replace("\"messageId\"", "\"eventId\""),
        json.replace("\"data\"", "\"payload\"")}) {
      assertThrows(Exception.class, () -> JsonSerialization.mapper.readValue(invalid, JsonSerialization.mapper.getTypeFactory().constructParametricType(Envelope.class, UserCreatedV1.class)));
    }
    assertThrows(IllegalArgumentException.class, () -> UserCreatedV1.builder().realmId("realm").build());
  }

  @Test void omitsAbsentDisplayName() throws Exception {
    String json = fixture().replace("\"displayName\": \"Nguyễn Văn An\"", "\"displayName\": null");
    Envelope<UserCreatedV1> event = JsonSerialization.mapper.readValue(json, JsonSerialization.mapper.getTypeFactory().constructParametricType(Envelope.class, UserCreatedV1.class));
    String encoded = JsonSerialization.writeValueAsString(event);
    assertFalse(encoded.contains("displayName"));
    assertTrue(encoded.contains("\"causationId\":null"));
    assertTrue(encoded.contains("\"traceparent\":null"));
  }
}
