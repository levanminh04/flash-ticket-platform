package com.flashticket.booking_service.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.projection.EventCancellationCandidate;
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
    var eventId = UUID.randomUUID();
    var first = new EventCancellationCandidate(eventId, UUID.randomUUID());
    var last = new EventCancellationCandidate(eventId, UUID.randomUUID());
    when(orderRepository.findEventCancellationCandidates(PageRequest.of(0, 2)))
        .thenReturn(List.of(first, last));
    worker.cancelOrdersForCancelledEvents();
    var ordered = inOrder(orderService);
    ordered.verify(orderService).cancelOrderForEventCancellation(first.getOrderId());
    ordered.verify(orderService).cancelOrderForEventCancellation(last.getOrderId());
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isSameAs(last);
  }

  @Test
  void continuesAndRetriesFailedEventOrderCancellations() {
    ReflectionTestUtils.setField(worker, "batchSize", 1);
    var bad = new EventCancellationCandidate(UUID.randomUUID(), UUID.randomUUID());
    var next = new EventCancellationCandidate(UUID.randomUUID(), UUID.randomUUID());
    var page = PageRequest.of(0, 1);
    when(orderRepository.findEventCancellationCandidates(page)).thenReturn(List.of(bad));
    when(orderRepository.findEventCancellationCandidatesAfter(bad.getEventId(), bad.getOrderId(), page))
        .thenReturn(List.of(next));
    when(orderRepository.findEventCancellationCandidatesAfter(next.getEventId(), next.getOrderId(), page))
        .thenReturn(List.of());
    doThrow(new IllegalStateException("commit rejected"))
        .when(orderService)
        .cancelOrderForEventCancellation(bad.getOrderId());
    worker.cancelOrdersForCancelledEvents();
    worker.cancelOrdersForCancelledEvents();
    verify(orderService).cancelOrderForEventCancellation(next.getOrderId());
    worker.cancelOrdersForCancelledEvents();
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isNull();
    worker.cancelOrdersForCancelledEvents();
    var restarted = new EventCancellationWorker(orderRepository, orderService);
    ReflectionTestUtils.setField(restarted, "batchSize", 1);
    restarted.cancelOrdersForCancelledEvents();
    verify(orderService, times(3)).cancelOrderForEventCancellation(bad.getOrderId());
  }

  @Test
  void preservesEventCancellationCursorWhenQueryFails() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var cursor = new EventCancellationCandidate(UUID.randomUUID(), UUID.randomUUID());
    ReflectionTestUtils.setField(worker, "lastCandidate", cursor);
    when(orderRepository.findEventCancellationCandidatesAfter(
            cursor.getEventId(), cursor.getOrderId(), PageRequest.of(0, 2)))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(List.of());
    assertThatThrownBy(worker::cancelOrdersForCancelledEvents)
        .isInstanceOf(IllegalStateException.class);
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isSameAs(cursor);
    worker.cancelOrdersForCancelledEvents();
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isNull();
    verifyNoInteractions(orderService);
  }

  @Test
  void stopsEventCancellationBatchWhenInterrupted() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var eventId = UUID.randomUUID();
    var first = new EventCancellationCandidate(eventId, UUID.randomUUID());
    var last = new EventCancellationCandidate(eventId, UUID.randomUUID());
    when(orderRepository.findEventCancellationCandidates(PageRequest.of(0, 2)))
        .thenReturn(List.of(first, last));
    doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              return null;
            })
        .when(orderService)
        .cancelOrderForEventCancellation(first.getOrderId());
    try {
      worker.cancelOrdersForCancelledEvents();
      verify(orderService, never()).cancelOrderForEventCancellation(last.getOrderId());
      assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isSameAs(first);
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
