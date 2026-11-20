package com.flashticket.booking_service.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.projection.ExpiredOrderCandidate;
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
    var first = new ExpiredOrderCandidate(UUID.randomUUID(), cutoff.minusSeconds(1));
    var last = new ExpiredOrderCandidate(UUID.randomUUID(), cutoff);
    when(orderRepository.currentDatabaseTime()).thenReturn(cutoff);
    when(orderRepository.findExpiredOrderCandidates(
            OrderState.PENDING_PAYMENT, cutoff, PageRequest.of(0, 2)))
        .thenReturn(List.of(first, last));
    worker.expireDueOrders();
    var ordered = inOrder(orderService);
    ordered.verify(orderService).expireOrder(first.getOrderId());
    ordered.verify(orderService).expireOrder(last.getOrderId());
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isSameAs(last);
    assertThat(ReflectionTestUtils.getField(worker, "cycleCutoff")).isEqualTo(cutoff);
  }

  @Test
  void continuesAfterOrderExpiryFailure() {
    ReflectionTestUtils.setField(worker, "batchSize", 3);
    var first = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH);
    var last = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH);
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH);
    when(orderRepository.findExpiredOrderCandidates(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, PageRequest.of(0, 3)))
        .thenReturn(List.of(first, last));
    doThrow(new IllegalStateException("commit failed"))
        .when(orderService)
        .expireOrder(first.getOrderId());
    assertThatCode(worker::expireDueOrders).doesNotThrowAnyException();
    verify(orderService).expireOrder(last.getOrderId());
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isNull();
    assertThat(ReflectionTestUtils.getField(worker, "cycleCutoff")).isNull();
  }

  @Test
  void advancesAndWrapsExpiryCursorAfterFailures() {
    ReflectionTestUtils.setField(worker, "batchSize", 1);
    var bad = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH.minusSeconds(1));
    var next = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH);
    var newCutoff = Instant.EPOCH.plusSeconds(5);
    var page = PageRequest.of(0, 1);
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH, newCutoff);
    when(orderRepository.findExpiredOrderCandidates(OrderState.PENDING_PAYMENT, Instant.EPOCH, page))
        .thenReturn(List.of(bad));
    when(orderRepository.findExpiredOrderCandidatesAfter(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, bad.getExpiresAt(), bad.getOrderId(), page))
        .thenReturn(List.of(next));
    when(orderRepository.findExpiredOrderCandidatesAfter(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, next.getExpiresAt(), next.getOrderId(), page))
        .thenReturn(List.of());
    when(orderRepository.findExpiredOrderCandidates(OrderState.PENDING_PAYMENT, newCutoff, page))
        .thenReturn(List.of(bad));
    doThrow(new IllegalStateException("bad order")).when(orderService).expireOrder(bad.getOrderId());
    worker.expireDueOrders();
    worker.expireDueOrders();
    verify(orderService).expireOrder(next.getOrderId());
    worker.expireDueOrders();
    verify(orderRepository).currentDatabaseTime();
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isNull();
    assertThat(ReflectionTestUtils.getField(worker, "cycleCutoff")).isNull();
    worker.expireDueOrders();
    verify(orderRepository).findExpiredOrderCandidates(OrderState.PENDING_PAYMENT, newCutoff, page);
    var restarted = new OrderExpiryWorker(orderRepository, orderService);
    ReflectionTestUtils.setField(restarted, "batchSize", 1);
    restarted.expireDueOrders();
    verify(orderService, times(3)).expireOrder(bad.getOrderId());
  }

  @Test
  void preservesExpiryCursorWhenCandidateQueryFails() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var cursor = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH.minusSeconds(1));
    ReflectionTestUtils.setField(worker, "lastCandidate", cursor);
    ReflectionTestUtils.setField(worker, "cycleCutoff", Instant.EPOCH);
    var page = PageRequest.of(0, 2);
    when(orderRepository.findExpiredOrderCandidatesAfter(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, cursor.getExpiresAt(), cursor.getOrderId(), page))
        .thenThrow(new IllegalStateException("database down"))
        .thenReturn(List.of());
    assertThatThrownBy(worker::expireDueOrders).isInstanceOf(IllegalStateException.class);
    assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isSameAs(cursor);
    assertThat(ReflectionTestUtils.getField(worker, "cycleCutoff")).isEqualTo(Instant.EPOCH);
    worker.expireDueOrders();
    verify(orderRepository, times(2))
        .findExpiredOrderCandidatesAfter(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, cursor.getExpiresAt(), cursor.getOrderId(), page);
    verify(orderRepository, never()).currentDatabaseTime();
    verifyNoInteractions(orderService);
  }

  @Test
  void stopsExpiryBatchWhenThreadIsInterrupted() {
    ReflectionTestUtils.setField(worker, "batchSize", 2);
    var first = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH);
    var last = new ExpiredOrderCandidate(UUID.randomUUID(), Instant.EPOCH);
    when(orderRepository.currentDatabaseTime()).thenReturn(Instant.EPOCH);
    when(orderRepository.findExpiredOrderCandidates(
            OrderState.PENDING_PAYMENT, Instant.EPOCH, PageRequest.of(0, 2)))
        .thenReturn(List.of(first, last));
    doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              return null;
            })
        .when(orderService)
        .expireOrder(first.getOrderId());
    try {
      worker.expireDueOrders();
      verify(orderService, never()).expireOrder(last.getOrderId());
      assertThat(ReflectionTestUtils.getField(worker, "lastCandidate")).isSameAs(first);
      assertThat(ReflectionTestUtils.getField(worker, "cycleCutoff")).isEqualTo(Instant.EPOCH);
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
