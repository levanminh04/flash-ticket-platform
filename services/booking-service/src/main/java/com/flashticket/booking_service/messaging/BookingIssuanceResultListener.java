package com.flashticket.booking_service.messaging;

import com.flashticket.booking_service.dto.message.BookingIssuanceResult;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingIssuanceResultListener {
  private final OrderService orderService;

  @RabbitListener(queues = "booking.tickets-issued.q", ackMode = "AUTO", concurrency = "1")
  public void handleTicketsIssued(Envelope<BookingIssuanceResult> message) {
    if (message == null
        || !"ticket.issued.v1".equals(message.messageType())
        || message.data() == null
        || message.data().state() != BookingIssuanceResult.State.COMPLETED)
      throw new AmqpRejectAndDontRequeueException(ErrorCode.INVALID_MESSAGE.name());
    try {
      // Service commit xong mới return; container AUTO ACK sau đó.
      orderService.processBookingIssuanceResult(message);
    } catch (BusinessException exception) {
      ErrorCode code = exception.getErrorCode();
      if (code == ErrorCode.INVALID_MESSAGE
          || code == ErrorCode.MESSAGE_ID_CONFLICT
          || code == ErrorCode.ISSUANCE_RESULT_CONFLICT
          || code == ErrorCode.RESERVATION_DATA_CONFLICT)
        throw new AmqpRejectAndDontRequeueException(code.name(), exception);
      throw exception;
    }
  }

  @RabbitListener(queues = "booking.issuance-failed.q", ackMode = "AUTO", concurrency = "1")
  public void handleIssuanceFailed(Envelope<BookingIssuanceResult> message) {
    if (message == null
        || !"booking.issuance-failed.v1".equals(message.messageType())
        || message.data() == null
        || message.data().state() != BookingIssuanceResult.State.FAILED)
      throw new AmqpRejectAndDontRequeueException(ErrorCode.INVALID_MESSAGE.name());
    try {
      orderService.processBookingIssuanceResult(message);
    } catch (BusinessException exception) {
      ErrorCode code = exception.getErrorCode();
      if (code == ErrorCode.INVALID_MESSAGE
          || code == ErrorCode.MESSAGE_ID_CONFLICT
          || code == ErrorCode.ISSUANCE_RESULT_CONFLICT
          || code == ErrorCode.RESERVATION_DATA_CONFLICT)
        throw new AmqpRejectAndDontRequeueException(code.name(), exception);
      throw exception;
    }
  }
}
