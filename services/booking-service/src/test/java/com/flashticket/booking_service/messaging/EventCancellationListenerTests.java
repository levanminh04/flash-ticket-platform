package com.flashticket.booking_service.messaging;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.EventCancellation;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

class EventCancellationListenerTests {
  @Test
  void delegatesEventCancellationToTransactionalService() {
    var service = mock(AvailabilityService.class);
    var data =
        new EventCancellation(
            EVENT,
            SEAT,
            "1",
            NOW,
            "admin",
            with(sales(), "eventStatus", SalesConfiguration.EventStatus.CANCELLED),
            new EventCancellation.AccessConfiguration(
                EVENT,
                "organizer",
                SalesConfiguration.EventStatus.CANCELLED,
                NOW.plusSeconds(7200),
                NOW.plusSeconds(10800),
                "1"));
    var message =
        new Envelope<>(MESSAGE, "event.cancelled.v1", 1, EVENT, "1", NOW, EVENT, null, null, data);
    new EventCancellationListener(service).handleEventCancelled(message);
    verify(service).processEventCancellation(message);
  }

  @Test
  void rejectsPermanentEventCancellationErrors() {
    for (var code :
        new ErrorCode[] {
          ErrorCode.INVALID_MESSAGE,
          ErrorCode.MESSAGE_ID_CONFLICT,
          ErrorCode.SALES_CONFIGURATION_CONFLICT
        }) {
      var service = mock(AvailabilityService.class);
      var failure = new BusinessException(code);
      doThrow(failure).when(service).processEventCancellation(any());
      assertThatThrownBy(() -> new EventCancellationListener(service).handleEventCancelled(null))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class)
          .hasCause(failure);
    }
  }

  @Test
  void propagatesEventCancellationDatabaseFailures() {
    var service = mock(AvailabilityService.class);
    var databaseFailure = new DataAccessResourceFailureException("test database unavailable");
    doThrow(databaseFailure).when(service).processEventCancellation(any());
    assertThatThrownBy(() -> new EventCancellationListener(service).handleEventCancelled(null))
        .isSameAs(databaseFailure);
    var retryableFailure = new BusinessException(ErrorCode.CONFIG_NOT_READY);
    doThrow(retryableFailure).when(service).processEventCancellation(any());
    assertThatThrownBy(() -> new EventCancellationListener(service).handleEventCancelled(null))
        .isSameAs(retryableFailure);
  }
}
