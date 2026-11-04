package com.flashticket.booking_service.entity;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EntityBehaviorTests {
  @Test
  void addingChildrenSetsBothSidesAndRejectsNull() {
    var order = new Order();
    var item = new OrderItem();
    var seat = new OrderItemSeat();
    var reservation = new Reservation();
    var allocation = new ReservationAllocation();
    order.addItem(item);
    item.addSeatAssignment(seat);
    reservation.addAllocation(allocation);
    assertThat(order.getItems()).containsExactly(item);
    assertThat(item.getOrder()).isSameAs(order);
    assertThat(item.getSeatAssignments()).containsExactly(seat);
    assertThat(seat.getOrderItem()).isSameAs(item);
    assertThat(reservation.getAllocations()).containsExactly(allocation);
    assertThat(allocation.getReservation()).isSameAs(reservation);
    assertThatNullPointerException().isThrownBy(() -> order.addItem(null));
    assertThatNullPointerException().isThrownBy(() -> item.addSeatAssignment(null));
    assertThatNullPointerException().isThrownBy(() -> reservation.addAllocation(null));
    assertThat(order.getItems()).hasSize(1);
    assertThat(item.getSeatAssignments()).hasSize(1);
    assertThat(reservation.getAllocations()).hasSize(1);
  }

  static Stream<Object[]> ids() {
    var p1 = new PurchaseLimitId();
    p1.setEventId(EVENT);
    p1.setBuyerSubject("buyer");
    var p2 = new PurchaseLimitId();
    p2.setEventId(EVENT);
    p2.setBuyerSubject("buyer");
    var p3 = new PurchaseLimitId();
    p3.setEventId(EVENT);
    p3.setBuyerSubject("other");
    var i1 = new InboxMessageId();
    i1.setConsumerName("consumer");
    i1.setMessageId(MESSAGE);
    var i2 = new InboxMessageId();
    i2.setConsumerName("consumer");
    i2.setMessageId(MESSAGE);
    var i3 = new InboxMessageId();
    i3.setConsumerName("other");
    i3.setMessageId(MESSAGE);
    var s1 = new OrderItemSeatId();
    s1.setOrderItemId(ORDER);
    s1.setSeatId(SEAT);
    var s2 = new OrderItemSeatId();
    s2.setOrderItemId(ORDER);
    s2.setSeatId(SEAT);
    var s3 = new OrderItemSeatId();
    s3.setOrderItemId(ORDER);
    s3.setSeatId(TICKET);
    var r1 = new IdempotencyRequestId();
    r1.setActorSubject("buyer");
    r1.setOperation("create");
    r1.setRequestKey(MESSAGE);
    var r2 = new IdempotencyRequestId();
    r2.setActorSubject("buyer");
    r2.setOperation("create");
    r2.setRequestKey(MESSAGE);
    var r3 = new IdempotencyRequestId();
    r3.setActorSubject("buyer");
    r3.setOperation("pay");
    r3.setRequestKey(MESSAGE);
    return Stream.of(
        new Object[] {p1, p2, p3},
        new Object[] {i1, i2, i3},
        new Object[] {s1, s2, s3},
        new Object[] {r1, r2, r3});
  }

  @ParameterizedTest
  @MethodSource("ids")
  void compositeKeysPreserveEqualityAndHashContract(Object first, Object equal, Object different) {
    assertThat(first).isEqualTo(equal);
    assertThat(equal).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(equal.hashCode());
    assertThat(first).isNotEqualTo(different).isNotEqualTo(null).isNotEqualTo("other");
    assertThat(new java.util.HashSet<>(java.util.List.of(first, equal, different))).hasSize(2);
  }
}
