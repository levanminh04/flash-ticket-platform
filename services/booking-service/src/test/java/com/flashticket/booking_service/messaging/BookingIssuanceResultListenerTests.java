package com.flashticket.booking_service.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.message.*;
import com.flashticket.booking_service.exception.*;
import com.flashticket.booking_service.order.service.OrderService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class BookingIssuanceResultListenerTests {
  @Mock OrderService orderService;
  @InjectMocks BookingIssuanceResultListener listener;

  @Test
  void delegatesIssuanceResultToTransactionalService() {
    for (boolean completed : List.of(true, false)) {
      UUID orderId = UUID.randomUUID();
      var data =
          new BookingIssuanceResult(
              orderId,
              UUID.randomUUID(),
              UUID.randomUUID(),
              completed
                  ? BookingIssuanceResult.State.COMPLETED
                  : BookingIssuanceResult.State.FAILED,
              completed ? 2 : 0,
              completed ? null : "ISSUANCE_REJECTED");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              completed ? "ticket.issued.v1" : "booking.issuance-failed.v1",
              1,
              orderId,
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              data);
      if (completed) listener.handleTicketsIssued(message);
      else listener.handleIssuanceFailed(message);
      verify(orderService).processBookingIssuanceResult(message);
    }
  }

  @Test
  void rejectsPermanentIssuanceResultErrors() {
    for (boolean completed : List.of(true, false)) {
      UUID orderId = UUID.randomUUID();
      var data =
          new BookingIssuanceResult(
              orderId,
              UUID.randomUUID(),
              UUID.randomUUID(),
              completed
                  ? BookingIssuanceResult.State.COMPLETED
                  : BookingIssuanceResult.State.FAILED,
              completed ? 2 : 0,
              completed ? null : "ISSUANCE_REJECTED");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              completed ? "ticket.issued.v1" : "booking.issuance-failed.v1",
              1,
              orderId,
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              data);
      for (var code :
          List.of(
              ErrorCode.INVALID_MESSAGE,
              ErrorCode.MESSAGE_ID_CONFLICT,
              ErrorCode.ISSUANCE_RESULT_CONFLICT,
              ErrorCode.RESERVATION_DATA_CONFLICT)) {
        var failure = new BusinessException(code);
        doThrow(failure).when(orderService).processBookingIssuanceResult(message);
        assertThatThrownBy(
                () -> {
                  if (completed) listener.handleTicketsIssued(message);
                  else listener.handleIssuanceFailed(message);
                })
            .isInstanceOf(AmqpRejectAndDontRequeueException.class)
            .hasCause(failure);
      }
      assertThatThrownBy(
              () -> {
                if (completed) listener.handleIssuanceFailed(message);
                else listener.handleTicketsIssued(message);
              })
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      var wrongState =
          new Envelope<>(
              message.messageId(),
              message.messageType(),
              1,
              orderId,
              "1",
              message.occurredAt(),
              message.correlationId(),
              null,
              null,
              new BookingIssuanceResult(
                  orderId, data.chargeId(), data.issuanceId(), null, 0, null));
      assertThatThrownBy(
              () -> {
                if (completed) listener.handleTicketsIssued(wrongState);
                else listener.handleIssuanceFailed(wrongState);
              })
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
    assertThatThrownBy(() -> listener.handleTicketsIssued(null))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    assertThatThrownBy(() -> listener.handleIssuanceFailed(null))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
  }

  @Test
  void propagatesIssuanceDependencyAndCommitFailures() {
    for (boolean completed : List.of(true, false)) {
      UUID orderId = UUID.randomUUID();
      var data =
          new BookingIssuanceResult(
              orderId,
              UUID.randomUUID(),
              UUID.randomUUID(),
              completed
                  ? BookingIssuanceResult.State.COMPLETED
                  : BookingIssuanceResult.State.FAILED,
              completed ? 2 : 0,
              completed ? null : "ISSUANCE_REJECTED");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              completed ? "ticket.issued.v1" : "booking.issuance-failed.v1",
              1,
              orderId,
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              data);
      for (RuntimeException failure :
          List.of(
              new BusinessException(ErrorCode.ISSUANCE_RESULT_NOT_READY),
              new DataAccessResourceFailureException("test commit failure"))) {
        doThrow(failure).when(orderService).processBookingIssuanceResult(message);
        assertThatThrownBy(
                () -> {
                  if (completed) listener.handleTicketsIssued(message);
                  else listener.handleIssuanceFailed(message);
                })
            .isSameAs(failure);
      }
    }
  }
}
