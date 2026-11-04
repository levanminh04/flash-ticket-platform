package com.flashticket.booking_service.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.message.*;
import com.flashticket.booking_service.entity.OutboxMessage;
import com.flashticket.booking_service.exception.*;
import com.flashticket.booking_service.messaging.repository.OutboxMessageRepository;
import com.flashticket.booking_service.messaging.service.MessagingService;
import com.flashticket.booking_service.order.service.OrderService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AcceptPaymentListenerTests {
  @Mock OrderService orderService;
  @Mock OutboxMessageRepository outboxMessageRepository;
  @Mock MessagingService messagingService;
  @InjectMocks AcceptPaymentListener listener;

  @Test
  void delegatesAcceptPaymentToTransactionalService() {
    listener.handleAcceptPayment(null);
    verify(orderService).acceptPayment(null);
    assertThatCode(
            () -> AcceptPaymentListener.class.getMethod("handleAcceptPayment", Envelope.class))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsPermanentAcceptPaymentErrorsWithoutRequeue() {
    for (var code :
        List.of(
            ErrorCode.INVALID_MESSAGE,
            ErrorCode.MESSAGE_ID_CONFLICT,
            ErrorCode.PAYMENT_SNAPSHOT_MISMATCH,
            ErrorCode.RESERVATION_DATA_CONFLICT)) {
      var exception = new BusinessException(code);
      doThrow(exception).when(orderService).acceptPayment(null);
      assertThatThrownBy(() -> listener.handleAcceptPayment(null))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class)
          .hasCause(exception);
    }
  }

  @Test
  void propagatesDatabaseAndCommitFailures() {
    var failure = new DataAccessResourceFailureException("test database failure");
    when(orderService.acceptPayment(null)).thenThrow(failure);
    assertThatThrownBy(() -> listener.handleAcceptPayment(null)).isSameAs(failure);
    doThrow(new BusinessException(ErrorCode.CONFIG_NOT_READY))
        .when(orderService)
        .acceptPayment(null);
    assertThatThrownBy(() -> listener.handleAcceptPayment(null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void publishesOnlyDuePaymentDecisionOutboxIds() {
    var worker = new BookingOutboxWorker(outboxMessageRepository, messagingService);
    ReflectionTestUtils.setField(worker, "batchSize", 20);
    var row = new OutboxMessage();
    row.setMessageId(UUID.randomUUID());
    when(outboxMessageRepository
            .findByMessageTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(
                eq(List.of("booking.payment-decision.v1", "booking.order-completed.v1")),
                any(),
                eq(org.springframework.data.domain.PageRequest.of(0, 20))))
        .thenReturn(List.of(row));
    worker.publishDueBookingMessages();
    verify(messagingService).publishBookingMessage(row.getMessageId());
  }

  @Test
  void continuesOutboxBatchAfterOneRowFails() {
    var worker = new BookingOutboxWorker(outboxMessageRepository, messagingService);
    ReflectionTestUtils.setField(worker, "batchSize", 20);
    var first = new OutboxMessage();
    first.setMessageId(UUID.randomUUID());
    var second = new OutboxMessage();
    second.setMessageId(UUID.randomUUID());
    when(outboxMessageRepository
            .findByMessageTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(
                anyCollection(), any(), any()))
        .thenReturn(List.of(first, second));
    doThrow(new DataAccessResourceFailureException("test failure"))
        .when(messagingService)
        .publishBookingMessage(first.getMessageId());
    worker.publishDueBookingMessages();
    verify(messagingService).publishBookingMessage(second.getMessageId());
    reset(messagingService);
    doAnswer(
            call -> {
              Thread.currentThread().interrupt();
              return null;
            })
        .when(messagingService)
        .publishBookingMessage(first.getMessageId());
    try {
      worker.publishDueBookingMessages();
      verify(messagingService, never()).publishBookingMessage(second.getMessageId());
    } finally {
      Thread.interrupted();
    }
  }
}
