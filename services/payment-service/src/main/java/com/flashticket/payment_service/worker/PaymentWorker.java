package com.flashticket.payment_service.worker;

import com.flashticket.payment_service.service.MessagingService;
import com.flashticket.payment_service.service.PaymentSagaService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "payment.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PaymentWorker {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(PaymentWorker.class);
  private final MessagingService messaging;
  private final PaymentSagaService sagaService;
  private final RabbitTemplate rabbitTemplate;

  @Value("${payment.exchange:business.ex}")
  private String exchange;

  @Value("${payment.outbox.batch-size:20}")
  private int batchSize;

  @Value("${payment.outbox.lease:PT2M}")
  private Duration lease;

  @Value("${payment.outbox.confirm-timeout:PT5S}")
  private Duration confirmTimeout;

  @Scheduled(
      fixedDelayString = "${payment.outbox.poll-delay:1000}",
      initialDelayString = "${payment.worker.initial-delay:0}")
  public void publishOutbox() {
    Instant now = Instant.now();
    var claimedMessages = messaging.claimOutbox(now, now.plus(lease), batchSize);
    for (var storedMessage : claimedMessages) {
      try {
        var properties = new MessageProperties();
        properties.setMessageId(storedMessage.getMessageId().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation = new CorrelationData(storedMessage.getMessageId().toString());
        // Confirm chỉ nói broker nhận message; không cập nhật Saga ở worker này.
        rabbitTemplate.send(
            exchange,
            storedMessage.getMessageType(),
            new Message(
                storedMessage.getPayload().toString().getBytes(StandardCharsets.UTF_8), properties),
            correlation);
        var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null)
          throw new IllegalStateException("Message not routed");
        messaging.markPublished(
            storedMessage.getMessageId(), storedMessage.getAttempts(), Instant.now());
      } catch (Exception ex) {
        messaging.rescheduleOutbox(
            storedMessage.getMessageId(),
            storedMessage.getAttempts(),
            Instant.now()
                .plusSeconds(Math.min(300, 1L << Math.min(8, storedMessage.getAttempts()))),
            "BROKER_DELIVERY_UNCONFIRMED");
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  @Scheduled(
      fixedDelayString = "${payment.saga.poll-delay:5000}",
      initialDelayString = "${payment.worker.initial-delay:0}")
  public void recoverSagas() {
    for (var orderId : sagaService.findRecoverableOrderIds(Instant.now(), batchSize)) {
      try {
        sagaService.recoverSaga(orderId);
      } catch (RuntimeException ex) {
        // TX đã rollback, nextAttemptAt cũ giữ công việc đến lần quét sau.
        // Không lấy lỗi mạng/timeout làm bằng chứng Ticket phát vé thất bại.
        log.warn(
            "Payment Saga recovery failed orderId={} exceptionType={}",
            orderId,
            ex.getClass().getSimpleName());
      }
    }
  }
}
