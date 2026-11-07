package com.flashticket.booking_service.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventCancellationWorkerTests {
  @Mock OrderRepository orderRepository;
  @Mock OrderService orderService;
  @InjectMocks EventCancellationWorker worker;

  @Test
  void pollsEventCancelledOrdersInBoundedBatches() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(orderRepository.findOrderIdsForEventCancellation(null, PageRequest.of(0, 2)))
        .thenReturn(ids);
    worker.cancelOrdersForCancelledEvents();
    var ordered = inOrder(orderService);
    ordered.verify(orderService).cancelOrderForEventCancellation(ids.getFirst());
    ordered.verify(orderService).cancelOrderForEventCancellation(ids.getLast());
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isEqualTo(ids.getLast());
  }

  @Test
  void continuesAndRetriesFailedEventOrderCancellations() {
    ReflectionTestUtils.setField(worker, "batchSize", 1);
    var bad = UUID.randomUUID();
    var next = UUID.randomUUID();
    when(orderRepository.findOrderIdsForEventCancellation(null, PageRequest.of(0, 1)))
        .thenReturn(List.of(bad));
    when(orderRepository.findOrderIdsForEventCancellation(bad, PageRequest.of(0, 1)))
        .thenReturn(List.of(next));
    when(orderRepository.findOrderIdsForEventCancellation(next, PageRequest.of(0, 1)))
        .thenReturn(List.of());
    doThrow(new IllegalStateException("commit rejected"))
        .when(orderService)
        .cancelOrderForEventCancellation(bad);
    worker.cancelOrdersForCancelledEvents();
    worker.cancelOrdersForCancelledEvents();
    verify(orderService).cancelOrderForEventCancellation(next);
    worker.cancelOrdersForCancelledEvents();
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isNull();
    worker.cancelOrdersForCancelledEvents();
    var restarted = new EventCancellationWorker(orderRepository, orderService);
    ReflectionTestUtils.setField(restarted, "batchSize", 1);
    restarted.cancelOrdersForCancelledEvents();
    verify(orderService, times(3)).cancelOrderForEventCancellation(bad);
  }

  @Test
  void preservesEventCancellationCursorWhenQueryFails() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var cursor = UUID.randomUUID();
    ReflectionTestUtils.setField(worker, "lastOrderId", cursor);
    when(orderRepository.findOrderIdsForEventCancellation(cursor, PageRequest.of(0, 2)))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(List.of());
    assertThatThrownBy(worker::cancelOrdersForCancelledEvents)
        .isInstanceOf(IllegalStateException.class);
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isEqualTo(cursor);
    worker.cancelOrdersForCancelledEvents();
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isNull();
    verifyNoInteractions(orderService);
  }

  @Test
  void stopsEventCancellationBatchWhenInterrupted() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(orderRepository.findOrderIdsForEventCancellation(null, PageRequest.of(0, 2)))
        .thenReturn(ids);
    doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              return null;
            })
        .when(orderService)
        .cancelOrderForEventCancellation(ids.getFirst());
    try {
      worker.cancelOrdersForCancelledEvents();
      verify(orderService, never()).cancelOrderForEventCancellation(ids.getLast());
      assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isEqualTo(ids.getFirst());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void rejectsNonPositiveEventCancellationBatchSize() {
    for (int size : new int[] {0, -1}) {
      ReflectionTestUtils.setField(worker, "batchSize", size);
      assertThatThrownBy(worker::cancelOrdersForCancelledEvents)
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(orderRepository, orderService);
  }
}
