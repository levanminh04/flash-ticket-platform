package com.flashticket.booking_service.messaging;

import com.flashticket.booking_service.dto.message.AcceptPayment;
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
public class AcceptPaymentListener {
  private final OrderService orderService;

  @RabbitListener(queues = "booking.accept-payment.q", ackMode = "AUTO", concurrency = "1")
  public void handleAcceptPayment(Envelope<AcceptPayment> message) {
    try {
      // Service qua proxy phải commit xong; listener trả về mới được AUTO ACK.
      orderService.acceptPayment(message);
    } catch (BusinessException exception) {
      ErrorCode code = exception.getErrorCode();
      if (code == ErrorCode.INVALID_MESSAGE
          || code == ErrorCode.MESSAGE_ID_CONFLICT
          || code == ErrorCode.PAYMENT_SNAPSHOT_MISMATCH
          || code == ErrorCode.RESERVATION_DATA_CONFLICT)
        throw new AmqpRejectAndDontRequeueException(code.name(), exception);
      throw exception;
    }
  }
}
