package com.flashticket.booking_service.messaging;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

class SalesConfigurationListenerTests {
  @Test
  void returnsSuccessfullyAfterSalesConfigurationServiceCompletes() {
    var service = mock(AvailabilityService.class);
    var message =
        new Envelope<>(
            MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
    new SalesConfigurationListener(service).handleSalesConfiguredEvent(message);
    verify(service).synchronizeSalesConfiguration(message);
  }

  @Test
  void rejectsPermanentSalesConfigurationFailuresWithoutRequeue() {
    for (var code :
        new ErrorCode[] {
          ErrorCode.INVALID_MESSAGE,
          ErrorCode.MESSAGE_ID_CONFLICT,
          ErrorCode.SALES_CONFIGURATION_CONFLICT
        }) {
      var service = mock(AvailabilityService.class);
      var exception = new BusinessException(code);
      doThrow(exception).when(service).synchronizeSalesConfiguration(any());
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
      assertThatThrownBy(
              () -> new SalesConfigurationListener(service).handleSalesConfiguredEvent(message))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class)
          .hasCause(exception);
    }
  }

  @Test
  void propagatesDatabaseFailuresToTheContainer() {
    var service = mock(AvailabilityService.class);
    var exception = new DataAccessResourceFailureException("test database unavailable");
    doThrow(exception).when(service).synchronizeSalesConfiguration(any());
    var message =
        new Envelope<>(
            MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
    assertThatThrownBy(
            () -> new SalesConfigurationListener(service).handleSalesConfiguredEvent(message))
        .isSameAs(exception);
  }
}
