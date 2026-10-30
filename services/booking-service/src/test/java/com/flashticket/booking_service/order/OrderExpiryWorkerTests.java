package com.flashticket.booking_service.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import java.time.Instant;
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
class OrderExpiryWorkerTests {
  @Mock OrderRepository orderRepository;
  @Mock OrderService orderService;
  @InjectMocks OrderExpiryWorker worker;

  @Test
  void pollsExpiredOrderIdsUsingDatabaseTime() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var cutoff = Instant.parse("2026-10-30T00:00:00Z");
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(orderRepository.currentDatabaseTime()).thenReturn(cutoff);
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, cutoff, null, PageRequest.of(0, 2)))
        .thenReturn(ids);
    worker.expireDueOrders();
    var ordered = inOrder(orderService);
    ordered.verify(orderService).expireOrder(ids.getFirst());
    ordered.verify(orderService).expireOrder(ids.getLast());
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isEqualTo(ids.getLast());
  }

  @Test
  void continuesAfterOrderExpiryFailure() {
    ReflectionTestUtils.setField(worker, "batchSize", 3);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH);
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, null, PageRequest.of(0, 3)))
        .thenReturn(ids);
    doThrow(new IllegalStateException("commit failed"))
        .when(orderService)
        .expireOrder(ids.getFirst());
    assertThatCode(worker::expireDueOrders).doesNotThrowAnyException();
    verify(orderService).expireOrder(ids.getLast());
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isNull();
  }

  @Test
  void advancesAndWrapsExpiryCursorAfterFailures() {
    ReflectionTestUtils.setField(worker, "batchSize", 1);
    UUID bad = UUID.randomUUID(), next = UUID.randomUUID();
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH);
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, null, PageRequest.of(0, 1)))
        .thenReturn(List.of(bad));
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, bad, PageRequest.of(0, 1)))
        .thenReturn(List.of(next));
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, next, PageRequest.of(0, 1)))
        .thenReturn(List.of());
    doThrow(new IllegalStateException("bad order")).when(orderService).expireOrder(bad);
    worker.expireDueOrders();
    worker.expireDueOrders();
    verify(orderService).expireOrder(next);
    worker.expireDueOrders();
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isNull();
    worker.expireDueOrders();
    var restarted = new OrderExpiryWorker(orderRepository, orderService);
    ReflectionTestUtils.setField(restarted, "batchSize", 1);
    restarted.expireDueOrders();
    verify(orderService, times(3)).expireOrder(bad);
  }

  @Test
  void preservesExpiryCursorWhenCandidateQueryFails() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var cursor = UUID.randomUUID();
    ReflectionTestUtils.setField(worker, "lastOrderId", cursor);
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH);
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, cursor, PageRequest.of(0, 2)))
        .thenThrow(new IllegalStateException("database down"))
        .thenReturn(List.of());
    assertThatThrownBy(worker::expireDueOrders).isInstanceOf(IllegalStateException.class);
    assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isEqualTo(cursor);
    worker.expireDueOrders();
    verify(orderRepository, times(2))
        .findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, cursor, PageRequest.of(0, 2));
    verifyNoInteractions(orderService);
  }

  @Test
  void stopsExpiryBatchWhenThreadIsInterrupted() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH);
    when(orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, null, PageRequest.of(0, 2)))
        .thenReturn(ids);
    doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              return null;
            })
        .when(orderService)
        .expireOrder(ids.getFirst());
    try {
      worker.expireDueOrders();
      verify(orderService, never()).expireOrder(ids.getLast());
      assertThat(ReflectionTestUtils.getField(worker, "lastOrderId")).isEqualTo(ids.getFirst());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void rejectsNonPositiveExpiryBatchSize() {
    for (int size : new int[] {0, -1}) {
      ReflectionTestUtils.setField(worker, "batchSize", size);
      assertThatThrownBy(worker::expireDueOrders).isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(orderRepository, orderService);
  }
}
