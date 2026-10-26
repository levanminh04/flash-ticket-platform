package com.flashticket.keycloak;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.keycloak.events.*;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.outbox.OutboxStore;
import org.keycloak.models.*;
import org.keycloak.util.JsonSerialization;

/** Captures only local form registrations in Keycloak's current transaction. */
public final class RegistrationEventListener implements EventListenerProvider {
  static final String KIND = "flash-ticket-registration";
  private final KeycloakSession session;
  private final OutboxStore store;

  public RegistrationEventListener(KeycloakSession session) {
    this(session, new OutboxStore(session));
  }

  RegistrationEventListener(KeycloakSession session, OutboxStore store) {
    this.session = session;
    this.store = store;
  }

  @Override public void onEvent(Event event) {
    if (event.getType() != EventType.REGISTER || event.getError() != null
        || event.getDetails() == null
        || !"form".equals(event.getDetails().get(Details.REGISTER_METHOD))) return;
    try {
      RealmModel realm = session.realms().getRealm(event.getRealmId());
      if (realm == null) throw new IllegalStateException("Missing realm");
      if (!"flash-ticket".equals(realm.getName())) return;
      if (!session.getTransactionManager().isActive()) throw new IllegalStateException("Inactive transaction");
      UserModel user = session.users().getUserById(realm, event.getUserId());
      if (user == null || event.getTime() <= 0) throw new IllegalStateException("Missing registration");
      String name = Stream.of(user.getLastName(), user.getFirstName())
          .filter(value -> value != null && !value.isBlank()).map(String::trim)
          .collect(Collectors.joining(" "));
      UserCreatedV1 message = new UserCreatedV1(1, UUID.fromString(event.getId()),
          UserCreatedV1.TYPE, Instant.ofEpochMilli(event.getTime()), event.getRealmId(),
          event.getUserId(), name.isEmpty() ? null : name);
      store.enqueuePending(KIND, event.getRealmId(), event.getRealmId(), null, event.getId(),
          UserCreatedV1.TYPE, JsonSerialization.writeValueAsString(message), null);
    } catch (Exception failure) {
      // EventBuilder catches listener exceptions; explicitly prevent a partial commit.
      session.getTransactionManager().setRollbackOnly();
      throw new IllegalStateException("REGISTRATION_OUTBOX_CAPTURE_FAILED");
    }
  }

  @Override public void onEvent(AdminEvent event, boolean includeRepresentation) {}
  @Override public void close() {}
}
