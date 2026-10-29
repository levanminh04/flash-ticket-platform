package com.flashticket.booking_service.messaging.service.impl;

import com.flashticket.booking_service.dto.message.PaymentDecision;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.mapper.OutboxMessageMapper;
import com.flashticket.booking_service.messaging.repository.InboxMessageRepository;
import com.flashticket.booking_service.messaging.repository.OutboxMessageRepository;
import com.flashticket.booking_service.messaging.service.MessagingService;
import com.flashticket.booking_service.order.repository.OrderRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessagingServiceImpl implements MessagingService {
  private final InboxMessageRepository inboxMessageRepository;
  private final OutboxMessageRepository outboxMessageRepository;
  private final OutboxMessageMapper outboxMessageMapper;
  private final RabbitTemplate rabbitTemplate;
  private final OrderRepository orderRepository;

  @Value("${booking.outbox.confirm-timeout:PT5S}")
  private Duration confirmTimeout;

  @Value("${booking.outbox.retry-delay:PT5S}")
  private Duration retryDelay;

  @Value("${spring.rabbitmq.template.exchange:business.ex}")
  private String exchange;

  @Value("${booking.reservation.lock-timeout}")
  private String lockTimeout;

  @Override
  @Transactional
  public void publishPaymentDecision(UUID messageId) {
    orderRepository.configureTransactionLockTimeout(lockTimeout);
    var lockedOutbox = outboxMessageRepository.findLockedByMessageId(messageId).orElse(null);
    if (lockedOutbox == null
        || lockedOutbox.getPublishedAt() != null
        || !"booking.payment-decision.v1".equals(lockedOutbox.getMessageType())) return;
    var now = orderRepository.currentDatabaseTime();
    if (lockedOutbox.getNextAttemptAt().isAfter(now)) return;
    ErrorCode failure = null;
    try {
      var envelope = outboxMessageMapper.toEnvelope(lockedOutbox, PaymentDecision.class);
      var properties = new MessageProperties();
      properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      properties.setMessageId(envelope.messageId().toString());
      properties.setCorrelationId(envelope.correlationId().toString());
      var message = rabbitTemplate.getMessageConverter().toMessage(envelope, properties);
      // Correlation ID riêng cho attempt; messageId nghiệp vụ không đổi khi gửi lại.
      var correlation = new CorrelationData(UUID.randomUUID().toString());
      rabbitTemplate.send(exchange, envelope.messageType(), message, correlation);
      var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.ack() || correlation.getReturned() != null)
        failure = ErrorCode.MESSAGE_PUBLISH_FAILED;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      failure = ErrorCode.MESSAGE_PUBLISH_FAILED;
    } catch (TimeoutException exception) {
      failure = ErrorCode.DEPENDENCY_TIMEOUT;
    } catch (ExecutionException | AmqpException exception) {
      failure = ErrorCode.MESSAGE_BROKER_UNAVAILABLE;
    } catch (com.flashticket.booking_service.exception.BusinessException
        | jakarta.validation.ConstraintViolationException
        | tools.jackson.core.JacksonException exception) {
      failure = ErrorCode.INVALID_MESSAGE;
    }
    // Không bắt lỗi save/commit: DB hỏng thì row vẫn pending, có thể gửi lại cùng ID.
    var completedAt = orderRepository.currentDatabaseTime();
    if (failure == null) {
      lockedOutbox.setPublishedAt(completedAt);
      lockedOutbox.setLastErrorCode(null);
    } else {
      lockedOutbox.setAttempts(
          lockedOutbox.getAttempts() == Integer.MAX_VALUE
              ? Integer.MAX_VALUE
              : lockedOutbox.getAttempts() + 1);
      lockedOutbox.setLastErrorCode(failure.name());
      lockedOutbox.setNextAttemptAt(completedAt.plus(retryDelay));
    }
    outboxMessageRepository.save(lockedOutbox);
  }
}
