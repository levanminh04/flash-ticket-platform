package com.flashticket.keycloak;

import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.keycloak.events.outbox.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;

/**
 * Bước 2: gửi một row outbox sang RabbitMQ và báo kết quả cho native drainer.
 * Class này không tự cập nhật outbox; drainer quản lý khóa DB, trạng thái và lịch retry.
 */
@RequiredArgsConstructor
public final class RabbitMqOutboxDeliveryHandler implements OutboxDeliveryHandler, AutoCloseable {
  private final ConnectionFactory factory;
  private Connection connection;

  @Override
  public String entryKind() { return RegistrationEventListener.KIND; }

  @Override
  public synchronized OutboxDeliveryResult deliver(KeycloakSession session, OutboxEntryEntity row) {
    try {
      if (connection == null || !connection.isOpen())
        connection = factory.newConnection();

      // Mỗi lượt có channel riêng; tự đóng khi gửi xong hoặc có lỗi.
      try (Channel channel = connection.createChannel()) {
        channel.confirmSelect();
        AtomicBoolean returned = new AtomicBoolean(false);

        // mandatory=true: broker trả lại message nếu không có queue nhận.
        channel.addReturnListener((ReturnCallback) reply -> returned.set(true));

        // Message persistent; giữ eventId cũ khi gửi lại để consumer chống trùng.
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .contentEncoding("UTF-8")
            .deliveryMode(2)
            .messageId(row.getCorrelationId())
            .build();

        channel.basicPublish("identity.ex", UserCreatedV1.TYPE, true, properties,
            row.getPayload().getBytes(StandardCharsets.UTF_8));

        // Chờ ACK tối đa 2 giây. NACK hoặc timeout sẽ ném exception xuống catch để retry.
        channel.waitForConfirmsOrDie(2000);

        // Broker ACK vẫn có thể không route được message đến queue nào.
        if (returned.get())
          return OutboxDeliveryResult.retry("BROKER_UNROUTABLE");

        // Drainer mới cập nhật outbox; consumer vẫn phải chống trùng nếu worker crash trước commit.
        return OutboxDeliveryResult.delivered();
      }

    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return OutboxDeliveryResult.retry("BROKER_INTERRUPTED");

    } catch (Exception failure) {
      // Chỉ trả mã lỗi an toàn; không lưu exception có thể chứa credential vào last_error.
      return OutboxDeliveryResult.retry("BROKER_UNAVAILABLE");
    }
  }

  @Override
  public synchronized void close() {
    if (connection != null) connection.abort();
    connection = null;
  }
}
