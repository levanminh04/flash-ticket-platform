package com.flashticket.keycloak;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.keycloak.events.*;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.outbox.OutboxStore;
import org.keycloak.models.*;
import org.keycloak.util.JsonSerialization;

/**
 * Bước 1: nhận sự kiện tự đăng ký và ghi message vào outbox của Keycloak.
 * Keycloak gọi listener trong transaction đang tạo tài khoản; worker sẽ gửi RabbitMQ sau đó.
 */
@RequiredArgsConstructor
public final class RegistrationEventListener implements EventListenerProvider {
  static final String KIND = "flash-ticket-registration";
  private final KeycloakSession session;
  private final OutboxStore outboxStore;
  private final boolean enabled;

  @Override
  public void onEvent(Event event) {
    if (!enabled) return;
    // Chỉ nhận đăng ký form thành công; bỏ login, đăng ký lỗi và đăng ký qua social/broker.
    if (event.getType() != EventType.REGISTER || event.getError() != null
        || event.getDetails() == null
        || !"form".equals(event.getDetails().get(Details.REGISTER_METHOD))) return;
    try {
      RealmModel realm = session.realms().getRealm(event.getRealmId());
      if (realm == null) throw new IllegalStateException("Missing realm");

      if (!"flash-ticket".equals(realm.getName())) return;

      if (!session.getTransactionManager().isActive())
        throw new IllegalStateException("Inactive transaction");

      // Keycloak đã tạo user trong transaction này nên có thể đọc profile qua session.
      UserModel user = session.users().getUserById(realm, event.getUserId());
      if (user == null || event.getTime() <= 0)
        throw new IllegalStateException("Missing registration");

      String displayName = Stream.of(user.getLastName(), user.getFirstName())
          .filter(value -> value != null && !value.isBlank()).map(String::trim)
          .collect(Collectors.joining(" "));

      UserCreatedV1 message = UserCreatedV1.builder()
          .schemaVersion(1)
          .eventId(UUID.fromString(event.getId()))
          .eventType(UserCreatedV1.TYPE)
          .occurredAt(Instant.ofEpochMilli(event.getTime()))
          .realmId(event.getRealmId())
          .identitySubject(event.getUserId())
          .displayName(displayName.isEmpty() ? null : displayName)
          .build();

      // Tham số: kind, realmId, ownerId, containerId, correlationId, type, payload, metadata.
      // Dùng realmId làm owner và eventId làm correlationId để native outbox chống enqueue trùng.
      // Đây chỉ là ghi DB; RabbitMQ đang tắt vẫn không ảnh hưởng request đăng ký.

      outboxStore.enqueuePending(KIND,
              event.getRealmId(),
              event.getRealmId(),
              null,
              event.getId(),
              UserCreatedV1.TYPE,
              JsonSerialization.writeValueAsString(message),
              null);
    } catch (Exception failure) {
      // Keycloak bắt exception của listener, nên throw đơn thuần có thể chưa hủy đăng ký.
      // Đánh dấu rollbackOnly để user và outbox cùng rollback nếu capture message thất bại.
      session.getTransactionManager().setRollbackOnly();
      throw new IllegalStateException("REGISTRATION_OUTBOX_CAPTURE_FAILED");
    }
  }

  // Admin tạo/sửa/xóa tài khoản nằm ngoài phạm vi đồng bộ lần này.
  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {}
  // Session thuộc Keycloak; listener không tự đóng session hoặc commit transaction.
  @Override
  public void close() {}
}
