package com.flashticket.booking_service.messaging;

import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.EventCancellation;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.observability.RabbitTelemetry;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventCancellationListener {
  private final AvailabilityService availabilityService;

  @RabbitListener(queues = "booking.event-cancelled.q", ackMode = "AUTO", concurrency = "1")
  public void handleEventCancelledDelivery(Envelope<EventCancellation> message, Message raw) {
    try (var telemetry = new RabbitTelemetry(raw)) {
      if (message != null) telemetry.context(message.messageId(), message.correlationId());
      handleEventCancelled(message);
      telemetry.result("SUCCESS", "TRANSACTION_COMMITTED");
    }
  }

  public void handleEventCancelled(Envelope<EventCancellation> message) {
    try {
      // Service proxy commit gate/inbox xong mới return; worker không nằm trong ACK này.
      availabilityService.processEventCancellation(message);
    } catch (BusinessException exception) {
      var code = exception.getErrorCode();
      if (code == ErrorCode.INVALID_MESSAGE
          || code == ErrorCode.MESSAGE_ID_CONFLICT
          || code == ErrorCode.SALES_CONFIGURATION_CONFLICT)
        throw new AmqpRejectAndDontRequeueException(code.name(), exception);
      throw exception;
    }
  }
}
