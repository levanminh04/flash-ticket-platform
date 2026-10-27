package com.flashticket.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.keycloak.events.*;
import org.keycloak.events.outbox.OutboxStore;
import org.keycloak.models.*;
import org.keycloak.util.JsonSerialization;
import org.mockito.ArgumentCaptor;

class RegistrationEventListenerTests {
  KeycloakSession session = mock(KeycloakSession.class);
  KeycloakTransactionManager tx = mock(KeycloakTransactionManager.class);
  RealmProvider realms = mock(RealmProvider.class);
  RealmModel realm = mock(RealmModel.class);
  UserProvider users = mock(UserProvider.class);
  UserModel user = mock(UserModel.class);
  OutboxStore store = mock(OutboxStore.class);
  Event event = new Event();

  @BeforeEach void setup() {
    when(session.getTransactionManager()).thenReturn(tx);
    when(tx.isActive()).thenReturn(true);
    when(session.realms()).thenReturn(realms);
    when(realms.getRealm("realm")).thenReturn(realm);
    when(realm.getName()).thenReturn("flash-ticket");
    when(session.users()).thenReturn(users);
    when(users.getUserById(realm, "subject")).thenReturn(user);
    when(user.getFirstName()).thenReturn(" An ");
    when(user.getLastName()).thenReturn("Nguyễn Văn");
    event.setId("9fe8679b-759a-4529-bc49-e522c59da03b");
    event.setRealmId("realm"); event.setUserId("subject");
    event.setTime(1793016000000L); event.setType(EventType.REGISTER);
    event.setDetails(Map.of(Details.REGISTER_METHOD, "form"));
  }

  @Test void capturesRegistrationInCurrentSessionAndNormalizesDisplayName() throws Exception {
    new RegistrationEventListener(session, store, true).onEvent(event);
    var payload = ArgumentCaptor.forClass(String.class);
    verify(store).enqueuePending(eq("flash-ticket-registration"), eq("realm"), eq("realm"),
        isNull(), eq(event.getId()), eq("identity.user-created.v1"), payload.capture(), isNull());
    Map<?,?> value = JsonSerialization.readValue(payload.getValue(), Map.class);
    assertEquals("subject", value.get("identitySubject"));
    assertEquals("Nguyễn Văn An", value.get("displayName"));
    assertTrue(value.get("occurredAt") instanceof String);
    assertFalse(value.containsKey("email"));
    verify(tx, never()).commit(); verify(tx, never()).begin();
  }

  @Test void ignoresOtherEventsAndNonFormRegistrations() {
    var listener = new RegistrationEventListener(session, store, true);
    for (EventType type : List.of(EventType.LOGIN, EventType.REGISTER_ERROR, EventType.UPDATE_PROFILE)) {
      event.setType(type); listener.onEvent(event);
    }
    event.setType(EventType.REGISTER); event.setDetails(Map.of()); listener.onEvent(event);
    event.setDetails(Map.of(Details.REGISTER_METHOD, "broker")); listener.onEvent(event);
    event.setDetails(null); listener.onEvent(event);
    event.setDetails(Map.of(Details.REGISTER_METHOD, "form"));
    new RegistrationEventListener(session, store, false).onEvent(event);
    verifyNoInteractions(store, session, tx);
  }

  @Test void ignoresErrorsAndOtherRealms() {
    var listener = new RegistrationEventListener(session, store, true);
    event.setError("failure"); listener.onEvent(event);
    event.setError(null); when(realm.getName()).thenReturn("master"); listener.onEvent(event);
    verifyNoInteractions(store);
  }

  @Test void enqueueFailureMarksRollbackOnlyEvenIfKeycloakCatchesListenerException() {
    when(store.enqueuePending(anyString(), anyString(), anyString(), isNull(),
        anyString(), anyString(), anyString(), isNull())).thenThrow(new IllegalStateException("db failed"));
    assertThrows(IllegalStateException.class, () -> new RegistrationEventListener(session, store, true).onEvent(event));
    verify(tx).setRollbackOnly();
  }

  @Test void missingUserOrInvalidIdCannotCommitWithoutOutbox() {
    event.setId("invalid");
    assertThrows(IllegalStateException.class, () -> new RegistrationEventListener(session, store, true).onEvent(event));
    verify(tx).setRollbackOnly(); verifyNoInteractions(store);
  }

  @Test void missingNameIsOmittedRatherThanNull() throws Exception {
    when(user.getFirstName()).thenReturn(null); when(user.getLastName()).thenReturn(" ");
    new RegistrationEventListener(session, store, true).onEvent(event);
    var payload = ArgumentCaptor.forClass(String.class);
    verify(store).enqueuePending(anyString(), anyString(), anyString(), isNull(),
        anyString(), anyString(), payload.capture(), isNull());
    assertFalse(payload.getValue().contains("displayName"));
  }
}
