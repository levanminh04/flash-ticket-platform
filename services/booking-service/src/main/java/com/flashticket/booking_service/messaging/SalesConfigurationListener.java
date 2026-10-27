package com.flashticket.booking_service.messaging;

import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Nhận cấu hình bán vé; container chỉ ACK sau khi service commit và listener trả về. */
@Component
@RequiredArgsConstructor
public class SalesConfigurationListener {
  private final AvailabilityService availabilityService;

  @RabbitListener(queues = "booking.sales-configured.q", ackMode = "AUTO", concurrency = "1")
  public void handleSalesConfiguredEvent(Envelope<SalesConfiguration> message) {
    try {
      availabilityService.synchronizeSalesConfiguration(message);
    } catch (BusinessException exception) {
      ErrorCode code = exception.getErrorCode();
      if (code == ErrorCode.INVALID_MESSAGE
          || code == ErrorCode.MESSAGE_ID_CONFLICT
          || code == ErrorCode.SALES_CONFIGURATION_CONFLICT) {
        // Retry không sửa được contract sai; broker đưa vào DLQ đã cấu hình.
        throw new AmqpRejectAndDontRequeueException(code.name(), exception);
      }
      throw exception;
    }
    // Không bắt lỗi DB/commit: container không ACK thành công khi transaction rollback.
  }
}
