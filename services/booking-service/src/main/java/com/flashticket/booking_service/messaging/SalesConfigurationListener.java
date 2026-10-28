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
      // Gọi service lưu DB; transaction phải commit xong thì lệnh gọi này mới trả về.
      availabilityService.synchronizeSalesConfiguration(message);
    } catch (BusinessException exception) {
      ErrorCode code = exception.getErrorCode();
      if (code == ErrorCode.INVALID_MESSAGE
          || code == ErrorCode.MESSAGE_ID_CONFLICT
          || code == ErrorCode.SALES_CONFIGURATION_CONFLICT) {
        // Dữ liệu sai thì xử lý lại vẫn sai => reject, không requeue, chuyển sang DLQ.
        throw new AmqpRejectAndDontRequeueException(code.name(), exception);
      }
      // Lỗi khác: ném ra ngoài để Spring xử lý theo cấu hình, có thể giao lại message.
      throw exception;
    }
    // Lỗi DB/commit không thuộc catch trên cũng được ném ra ngoài, không ACK thành công.
    // Chạy đến đây là thành công; method kết thúc thì Spring tự ACK vì đang dùng AUTO.
  }
}
