package com.flashticket.booking_service.order.repository;

import com.flashticket.booking_service.dto.projection.DailyOrderSalesTotals;
import com.flashticket.booking_service.dto.projection.DailyTicketSalesTotals;
import com.flashticket.booking_service.dto.projection.EventOrderSalesTotals;
import com.flashticket.booking_service.dto.projection.EventTicketSalesTotals;
import com.flashticket.booking_service.dto.projection.OrderStateTotals;
import com.flashticket.booking_service.dto.projection.StateTicketTotals;
import com.flashticket.booking_service.dto.projection.TicketTypeStateTotals;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.EventState;
import com.flashticket.booking_service.enums.OrderState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  @Query(
      """
      select o.id from Order o
      join Reservation r on r.order = o
      join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus = com.flashticket.booking_service.enums.EventState.CANCELLED
        and r.state in (com.flashticket.booking_service.enums.ReservationState.HELD,
                        com.flashticket.booking_service.enums.ReservationState.COMMITTED)
        and o.lifecycleState in (com.flashticket.booking_service.enums.OrderState.PENDING_PAYMENT,
                                 com.flashticket.booking_service.enums.OrderState.ISSUING,
                                 com.flashticket.booking_service.enums.OrderState.COMPLETED,
                                 com.flashticket.booking_service.enums.OrderState.CANCELLED)
        and (:afterOrderId is null or o.id > :afterOrderId)
      order by o.id asc
      """)
  List<UUID> findOrderIdsForEventCancellation(
      @Param("afterOrderId") UUID afterOrderId, Pageable pageable);

  @Query(
      """
      select o.id from Order o
      where o.lifecycleState = :state and o.expiresAt <= :cutoff
        and (:afterOrderId is null or o.id > :afterOrderId)
      order by o.id asc
      """)
  List<UUID> findExpiredOrderIds(
      @Param("state") OrderState state,
      @Param("cutoff") Instant cutoff,
      @Param("afterOrderId") UUID afterOrderId,
      Pageable pageable);

  @Query("select o.eventId from Order o where o.id = :orderId")
  Optional<UUID> findEventIdById(@Param("orderId") UUID orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Order> findLockedById(UUID id);

  @Query("select o.eventId from Order o where o.id = :orderId and o.buyerSubject = :buyerSubject")
  Optional<UUID> findEventIdByIdAndBuyerSubject(
      @Param("orderId") UUID orderId, @Param("buyerSubject") String buyerSubject);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Order> findLockedByIdAndBuyerSubject(UUID id, String buyerSubject);

  Optional<Order> findByIdAndBuyerSubject(UUID id, String buyerSubject);

  // Deferred join bước 1: chỉ phân trang trên tbl_orders để DB làm limit/offset + count.
  @Query(
      value =
          """
          select o.id from Order o
          where o.buyerSubject = :buyerSubject
            and (:state is null or o.lifecycleState = :state)
          """,
      countQuery =
          """
          select count(o) from Order o
          where o.buyerSubject = :buyerSubject
            and (:state is null or o.lifecycleState = :state)
          """)
  Page<UUID> findOrderIdsByBuyerSubject(
      @Param("buyerSubject") String buyerSubject,
      @Param("state") OrderState state,
      Pageable pageable);

  // Deferred join bước 2: nạp đơn + item của đúng các id trên trang, không kèm Pageable
  // nên Hibernate không phải phân trang trong bộ nhớ khi fetch join collection.
  @Query("select distinct o from Order o left join fetch o.items where o.id in :orderIds")
  List<Order> findWithItemsByIdIn(@Param("orderIds") List<UUID> orderIds);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.OrderStateTotals(
        o.lifecycleState, count(o), sum(o.subtotal), sum(o.discount), sum(o.total))
      from Order o
      where o.eventId = :eventId
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.lifecycleState
      """)
  List<OrderStateTotals> sumOrderTotalsByState(
      @Param("eventId") UUID eventId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  // Tách khỏi query trên để sum(o.total) không bị nhân lên theo số item của đơn.
  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.TicketTypeStateTotals(
        o.lifecycleState, i.ticketTypeId, i.ticketTypeName, sum(i.quantity), sum(i.lineSubtotal))
      from OrderItem i join i.order o
      where o.eventId = :eventId
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.lifecycleState, i.ticketTypeId, i.ticketTypeName
      """)
  List<TicketTypeStateTotals> sumTicketTypeTotalsByState(
      @Param("eventId") UUID eventId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.OrderStateTotals(o.lifecycleState, count(o), sum(o.subtotal), sum(o.discount), sum(o.total))
      from Order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.lifecycleState
      """)
  List<OrderStateTotals> sumSystemOrderTotalsByState(
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.StateTicketTotals(o.lifecycleState, sum(i.quantity))
      from OrderItem i join i.order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.lifecycleState
      """)
  List<StateTicketTotals> sumSystemTicketTotalsByState(
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates);

  // E1: hàm PostgreSQL và group-by biểu thức; không phải JPQL portable.
  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.DailyOrderSalesTotals(
        cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string), count(o), sum(o.total))
      from Order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates
        and o.lifecycleState in :soldStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string)
      """)
  List<DailyOrderSalesTotals> sumSystemDailyOrderSales(
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates);

  // E1: hàm PostgreSQL và group-by biểu thức; không phải JPQL portable.
  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.DailyTicketSalesTotals(
        cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string), sum(i.quantity))
      from OrderItem i join i.order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates
        and o.lifecycleState in :soldStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string)
      """)
  List<DailyTicketSalesTotals> sumSystemDailyTicketSales(
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates);

  @Query(
      value =
          """
      select s.eventId as eventId, count(o) as soldOrderCount, coalesce(sum(o.total), 0) as soldRevenue
      from EventSalesSnapshot s
      left join Order o on o.eventId = s.eventId
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
        and o.lifecycleState in :soldStates

      where s.eventStatus in :eventStates
      group by s.eventId
      """,
      countQuery =
          """
      select count(s) from EventSalesSnapshot s
      where s.eventStatus in :eventStates
      """)
  Page<Object[]> findSystemEventOrderSales(
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates,
      Pageable pageable);

  @Query(
      value =
          """
      select s.eventId as eventId, coalesce(sum(i.quantity), 0) as soldTicketCount
      from EventSalesSnapshot s
      left join Order o on o.eventId = s.eventId
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
        and o.lifecycleState in :soldStates
      left join o.items i
      where s.eventStatus in :eventStates
      group by s.eventId
      """,
      countQuery =
          """
      select count(s) from EventSalesSnapshot s
      where s.eventStatus in :eventStates
      """)
  Page<Object[]> findSystemEventTicketSales(
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates,
      Pageable pageable);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.OrderStateTotals(o.lifecycleState, count(o), sum(o.subtotal), sum(o.discount), sum(o.total))
      from Order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.lifecycleState
      """)
  List<OrderStateTotals> sumOrganizerOrderTotalsByState(
      @Param("organizerSubject") String organizerSubject,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.StateTicketTotals(o.lifecycleState, sum(i.quantity))
      from OrderItem i join i.order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.lifecycleState
      """)
  List<StateTicketTotals> sumOrganizerTicketTotalsByState(
      @Param("organizerSubject") String organizerSubject,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates);

  // E1: hàm PostgreSQL và group-by biểu thức; không phải JPQL portable.
  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.DailyOrderSalesTotals(
        cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string), count(o), sum(o.total))
      from Order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
        and o.lifecycleState in :soldStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string)
      """)
  List<DailyOrderSalesTotals> sumOrganizerDailyOrderSales(
      @Param("organizerSubject") String organizerSubject,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates);

  // E1: hàm PostgreSQL và group-by biểu thức; không phải JPQL portable.
  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.DailyTicketSalesTotals(
        cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string), sum(i.quantity))
      from OrderItem i join i.order o join EventSalesSnapshot s on s.eventId = o.eventId
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
        and o.lifecycleState in :soldStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by cast(function('to_char', function('timezone', 'Asia/Ho_Chi_Minh', o.createdAt), 'YYYY-MM-DD') as string)
      """)
  List<DailyTicketSalesTotals> sumOrganizerDailyTicketSales(
      @Param("organizerSubject") String organizerSubject,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates);

  @Query(
      value =
          """
      select s.eventId as eventId, count(o) as soldOrderCount, coalesce(sum(o.total), 0) as soldRevenue
      from EventSalesSnapshot s
      left join Order o on o.eventId = s.eventId
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
        and o.lifecycleState in :soldStates

      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
      group by s.eventId
      """,
      countQuery =
          """
      select count(s) from EventSalesSnapshot s
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
      """)
  Page<Object[]> findOrganizerEventOrderSales(
      @Param("organizerSubject") String organizerSubject,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates,
      Pageable pageable);

  @Query(
      value =
          """
      select s.eventId as eventId, coalesce(sum(i.quantity), 0) as soldTicketCount
      from EventSalesSnapshot s
      left join Order o on o.eventId = s.eventId
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
        and o.lifecycleState in :soldStates
      left join o.items i
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
      group by s.eventId
      """,
      countQuery =
          """
      select count(s) from EventSalesSnapshot s
      where s.eventStatus in :eventStates and s.organizerSubject = :organizerSubject
      """)
  Page<Object[]> findOrganizerEventTicketSales(
      @Param("organizerSubject") String organizerSubject,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("eventStates") List<EventState> eventStates,
      @Param("soldStates") List<OrderState> soldStates,
      Pageable pageable);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.EventOrderSalesTotals(o.eventId, count(o), sum(o.total))
      from Order o
      where o.eventId in :eventIds and o.lifecycleState in :soldStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.eventId
      """)
  List<EventOrderSalesTotals> sumOrderSalesByEventIds(
      @Param("eventIds") List<UUID> eventIds,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("soldStates") List<OrderState> soldStates);

  @Query(
      """
      select new com.flashticket.booking_service.dto.projection.EventTicketSalesTotals(o.eventId, sum(i.quantity))
      from OrderItem i join i.order o
      where o.eventId in :eventIds and o.lifecycleState in :soldStates
        and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive
      group by o.eventId
      """)
  List<EventTicketSalesTotals> sumTicketSalesByEventIds(
      @Param("eventIds") List<UUID> eventIds,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("soldStates") List<OrderState> soldStates);

  // Lấy giờ thực sau khi chờ khóa để kiểm tra thời hạn bán vé.
  @Query(value = "select clock_timestamp()", nativeQuery = true)
  Instant currentDatabaseTime();

  // Chỉ giới hạn chờ khóa trong transaction tạo đơn hiện tại.
  @Query(value = "select set_config('lock_timeout', :timeout, true)", nativeQuery = true)
  String configureTransactionLockTimeout(@Param("timeout") String timeout);
}
