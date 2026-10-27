package com.flashticket.user_service.messaging.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;

/**
 * Bước 3: nhận message RabbitMQ → kiểm contract → đảm bảo hồ sơ MongoDB. Spring ACK khi method kết
 * thúc thành công; ACK không gộp chung transaction với MongoDB.
 */
public final class IdentityUserCreatedListener {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityUserCreatedListener.class);
  private final ObjectMapper objectMapper;
  private final IdentityUserRegistrar registrar;

  @Value("${KEYCLOAK_IDENTITY_REALM_ID}")
  private String realmId;

  public IdentityUserCreatedListener(ObjectMapper objectMapper, IdentityUserRegistrar registrar) {
    this.objectMapper = objectMapper;
    this.registrar = registrar;
  }

  @RabbitListener(
      queues = "user.identity.q",
      concurrency = "1",
      containerFactory = "identityRabbitListenerContainerFactory")
  public void onMessage(Message message) {
    UserCreatedV1 event;
    try {
      if (!UserCreatedV1.TYPE.equals(message.getMessageProperties().getReceivedRoutingKey())
          || message.getBody().length > 65536)
        throw new IllegalArgumentException("Invalid envelope");

      event = objectMapper.readValue(message.getBody(), UserCreatedV1.class);

      // Subject chỉ có ý nghĩa trong realm đã cấu hình.
      if (!realmId.equals(event.realmId())) throw new IllegalArgumentException("Unexpected realm");

    } catch (RuntimeException invalid) {
      // Lỗi dữ liệu không tự hết khi retry: reject không requeue để broker chuyển vào DLQ.
      LOG.warn("Identity message rejected: INVALID_IDENTITY_EVENT");
      throw new AmqpRejectAndDontRequeueException("INVALID_IDENTITY_EVENT");
    }
    try {
      // Spring retry tại registrar khi gặp lỗi DB tạm thời.
      registrar.ensureRegistered(event);

    } catch (RuntimeException exhausted) {
      // Hết retry hoặc lỗi DB vĩnh viễn: giữ message ở DLQ để sửa nguyên nhân rồi replay.
      LOG.error("Identity write rejected: IDENTITY_WRITE_FAILED eventId={}", event.eventId());
      throw new AmqpRejectAndDontRequeueException("IDENTITY_WRITE_FAILED");
    }
    // Sau khi method kết thúc, container sẽ ACK; redelivery được xử lý idempotent.
    LOG.info("Identity profile ensured eventId={}", event.eventId());
  }
}
