package com.flashticket.keycloak;

import com.rabbitmq.client.ConnectionFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.keycloak.Config;
import org.keycloak.events.*;
import org.keycloak.events.outbox.*;
import org.keycloak.models.*;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.timer.TimerProvider;

/**
 * Keycloak tạo một factory dùng chung để đọc cấu hình, tạo listener và quản lý worker.
 * Worker là OutboxDrainerTask có sẵn của Keycloak, không phải một service Spring riêng.
 */
public final class RegistrationEventListenerFactory implements EventListenerProviderFactory {
  // ID dùng khi bật listener tại Realm Settings → Events.
  public static final String ID = "flash-ticket-registration-outbox";
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private KeycloakSessionFactory sessions;
  private RabbitMqOutboxDeliveryHandler publisher;
  private boolean enabled;
  private long interval;

  @Override
  public void init(Config.Scope config) {
    // Chỉ chuẩn bị cấu hình; publisher sẽ kết nối RabbitMQ khi thực sự có message cần gửi.
    enabled = Boolean.parseBoolean(env("FT_IDENTITY_OUTBOX_ENABLED", "true"));
    interval = Long.parseLong(env("FT_IDENTITY_OUTBOX_INTERVAL_MS", "2000"));
    if (interval < 1000)
      throw new IllegalArgumentException("Outbox interval must be >= 1000ms");
    if (!enabled) return;
    ConnectionFactory connection = new ConnectionFactory();
    connection.setHost(env("FT_IDENTITY_RABBITMQ_HOST", "rabbitmq"));
    connection.setPort(Integer.parseInt(env("FT_IDENTITY_RABBITMQ_PORT", "5672")));
    connection.setVirtualHost(env("FT_IDENTITY_RABBITMQ_VHOST", "/"));
    connection.setUsername(required("FT_IDENTITY_RABBITMQ_USERNAME"));
    connection.setPassword(required("FT_IDENTITY_RABBITMQ_PASSWORD"));
    // Native outbox retry sẽ gọi publisher lại, kể cả khi lần kết nối đầu tiên thất bại.
    connection.setAutomaticRecoveryEnabled(false);
    // Giới hạn thời gian chờ mạng để worker không giữ khóa row outbox quá lâu.
    connection.setConnectionTimeout(2000);
    connection.setHandshakeTimeout(2000);
    connection.setChannelRpcTimeout(2000);
    connection.setRequestedHeartbeat(15);
    publisher = new RabbitMqOutboxDeliveryHandler(connection);
  }

  private static String env(String key, String fallback) {
    return System.getenv().getOrDefault(key, fallback);
  }
  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }

  @Override
  public EventListenerProvider create(KeycloakSession session) {
    // Outbox dùng chính session của request để ghi cùng transaction tạo user.
    // Listener tự bỏ qua event khi disabled; factory không cần một lớp no-op riêng.
    return new RegistrationEventListener(session, new OutboxStore(session), enabled);
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    sessions = factory;
    factory.register(event -> {
      // Đợi Keycloak chuẩn bị DB xong rồi mới schedule, đúng một lần cho factory này.
      if (event instanceof PostMigrationEvent && enabled && scheduled.compareAndSet(false, true)) {
        // Lỗi broker: native worker hẹn lần sau theo backoff, có jitter để giãn các lần thử.
        OutboxBackoff backoff = new OutboxBackoff(Integer.MAX_VALUE, List.of(Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60)));
        // Thứ tự: kind, batchSize, backoff, deadLetterRetention, deliveredRetention, pendingMaxAge.
        // Chỉ dọn DELIVERED sau 7 ngày; không tự xóa message lỗi hoặc làm pending hết hạn.
        OutboxConfig settings = new OutboxConfig(RegistrationEventListener.KIND, 1, backoff,
            null, Duration.ofDays(7), null);
        // ponytail: mỗi tick 1 row để giới hạn thời gian giữ khóa; tăng batch khi đo thấy backlog.
        try (KeycloakSession session = factory.create()) {
          // Timer tạo session/transaction riêng cho mỗi lượt worker.
          // Native drainer khóa row đến hạn, gọi publisher, rồi lưu kết quả/retry vào DB.
          session.getProvider(TimerProvider.class).scheduleTask(
              new OutboxDrainerTask(settings, publisher, OutboxStore::new), interval, ID);
        }
      }
    });
  }

  @Override
  public String getId() { return ID; }
  @Override
  public void close() {
    // Hủy worker và đóng kết nối broker khi Keycloak shutdown; không xóa dữ liệu pending.
    if (scheduled.get() && sessions != null) {
      try (KeycloakSession session = sessions.create()) {
        session.getProvider(TimerProvider.class).cancelTask(ID);
      }
    }
    if (publisher != null) publisher.close();
  }
}
