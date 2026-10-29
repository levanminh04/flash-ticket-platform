package com.flashticket.booking_service.order.service.impl;

import com.flashticket.booking_service.availability.repository.*;
import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.request.OrderSelection;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import com.flashticket.booking_service.dto.snapshot.SeatSelectionSnapshot;
import com.flashticket.booking_service.dto.snapshot.SelectionSnapshot;
import com.flashticket.booking_service.entity.*;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.EventState;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.enums.PromotionUsageState;
import com.flashticket.booking_service.enums.ReservationState;
import com.flashticket.booking_service.enums.ResourceKind;
import com.flashticket.booking_service.enums.SeatState;
import com.flashticket.booking_service.enums.SectorType;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.mapper.OrderMapper;
import com.flashticket.booking_service.order.repository.IdempotencyRequestRepository;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import com.flashticket.booking_service.promotion.repository.PromotionUsageRepository;
import com.flashticket.booking_service.reservation.repository.ReservationRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
  private final OrderRepository orderRepository;
  private final IdempotencyRequestRepository idempotencyRequestRepository;
  private final OrderMapper orderMapper;
  private final EventSalesSnapshotRepository eventSalesSnapshotRepository;
  private final PurchaseLimitRepository purchaseLimitRepository;
  private final TicketTypeInventoryRepository ticketTypeInventoryRepository;
  private final SectorInventoryRepository sectorInventoryRepository;
  private final SeatInventoryRepository seatInventoryRepository;
  private final ReservationRepository reservationRepository;
  private final JsonMapper objectMapper;
  private final PromotionUsageRepository promotionUsageRepository;
  private final Validator validator;

  @Value("${booking.reservation.hold-duration}")
  private Duration reservationHoldDuration;

  @Value("${booking.reservation.lock-timeout}")
  private String reservationLockTimeout;

  @Override
  @Transactional
  public PurchaseSnapshot preparePurchaseSnapshot(String buyerSubject, UUID orderId) {
    if (buyerSubject == null || buyerSubject.isBlank())
      throw new BusinessException(ErrorCode.UNAUTHENTICATED);
    if (orderId == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    orderRepository.configureTransactionLockTimeout(reservationLockTimeout);
    UUID eventId =
        orderRepository
            .findEventIdByIdAndBuyerSubject(orderId, buyerSubject)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    var storedSnapshot =
        eventSalesSnapshotRepository
            .findForBookingByEventId(eventId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    var lockedOrder =
        orderRepository
            .findLockedByIdAndBuyerSubject(orderId, buyerSubject)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    var storedReservation =
        reservationRepository
            .findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    var decisionAt = orderRepository.currentDatabaseTime();
    if (storedSnapshot.getEventStatus() == EventState.CANCELLED
        || lockedOrder.getLifecycleState() != OrderState.PENDING_PAYMENT
        || storedReservation.getState() != ReservationState.HELD)
      throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE);
    if (!decisionAt.isBefore(lockedOrder.getExpiresAt()))
      throw new BusinessException(ErrorCode.ORDER_EXPIRED);
    if (!eventId.equals(storedReservation.getEventId())
        || !buyerSubject.equals(storedReservation.getBuyerSubject())
        || !lockedOrder.getExpiresAt().equals(storedReservation.getExpiresAt()))
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    // Cùng khóa với apply voucher: đọc lại sau khi chờ, không cố định tiền lần thứ hai.
    if (lockedOrder.getPaymentFrozenAt() != null) {
      var frozen = lockedOrder.getFrozenPurchaseSnapshot();
      if (frozen == null
          || !validator.validate(frozen).isEmpty()
          || !lockedOrder.getId().equals(frozen.orderId())
          || !eventId.equals(frozen.eventId())
          || !buyerSubject.equals(frozen.buyerSubject())
          || !Objects.equals(
              lockedOrder.getPaymentSnapshotVersion(), Long.valueOf(frozen.sourceOrderVersion()))
          || !lockedOrder.getPaymentFrozenAt().equals(frozen.frozenAt())
          || !lockedOrder.getExpiresAt().equals(frozen.expiresAt())
          || !lockedOrder.getTotal().toPlainString().equals(frozen.total())
          || !lockedOrder.getSubtotal().toPlainString().equals(frozen.subtotal())
          || !lockedOrder.getDiscount().toPlainString().equals(frozen.discount()))
        throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
      return frozen;
    }
    if (lockedOrder.getFrozenPurchaseSnapshot() != null
        || lockedOrder.getPaymentSnapshotVersion() != null)
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    var storedUsage =
        promotionUsageRepository
            .findByReservationIdAndStateIn(
                storedReservation.getId(),
                List.of(PromotionUsageState.HELD, PromotionUsageState.USED))
            .orElse(null);
    if (storedUsage != null
        && (storedUsage.getState() != PromotionUsageState.HELD
            || !eventId.equals(storedUsage.getEventId())
            || !buyerSubject.equals(storedUsage.getBuyerSubject())
            || !lockedOrder.getExpiresAt().equals(storedUsage.getExpiresAt())
            || storedUsage.getDiscount().compareTo(lockedOrder.getDiscount()) != 0
            || storedUsage.getCurrency() != lockedOrder.getCurrency()))
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    var frozen =
        buildPurchaseSnapshot(
            lockedOrder, storedSnapshot.getConfigurationSnapshot(), storedUsage, decisionAt);
    if (!validator.validate(frozen).isEmpty())
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    // Không so version order với config hiện tại để từ chối: tiền lấy từ OrderItem đã lưu.
    lockedOrder.setFrozenPurchaseSnapshot(frozen);
    lockedOrder.setPaymentFrozenAt(decisionAt);
    lockedOrder.setPaymentSnapshotVersion(1L);
    lockedOrder.setUpdatedAt(decisionAt);
    orderRepository.save(lockedOrder);
    return frozen;
  }

  private PurchaseSnapshot buildPurchaseSnapshot(
      Order lockedOrder,
      SalesConfiguration storedConfiguration,
      PromotionUsage storedPromotionUsage,
      Instant decisionAt) {
    if (storedConfiguration == null
        || !lockedOrder.getEventId().equals(storedConfiguration.eventId())
        || lockedOrder.getSalesMode() != storedConfiguration.mode())
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    if (lockedOrder.getItems().isEmpty()
        || lockedOrder.getItems().stream()
            .anyMatch(
                storedItem ->
                    !lockedOrder.getEventId().equals(storedItem.getEventId())
                        || storedItem.getCurrency() != lockedOrder.getCurrency()
                        || storedItem.getSelectionSnapshot() == null
                        || storedItem.getSelectionSnapshot().seats() == null))
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    if (storedPromotionUsage != null
        && (!lockedOrder.getEventId().equals(storedPromotionUsage.getPromotion().getEventId())
            || storedPromotionUsage.getPromotion().getConfigurationSnapshot() == null
            || storedPromotionUsage.getPromotion().getConfigurationSnapshot().configuration()
                == null)) throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    var items =
        lockedOrder.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getId))
            .map(
                item ->
                    new PurchaseSnapshot.Item(
                        item.getId(),
                        item.getTicketTypeId(),
                        item.getTicketTypeName(),
                        item.getQuantity(),
                        item.getUnitPrice().toPlainString(),
                        item.getLineSubtotal().toPlainString(),
                        item.getSectorId(),
                        item.getSelectionSnapshot().seats().stream()
                            .sorted(Comparator.comparing(SeatSelectionSnapshot::seatId))
                            .toList()))
            .toList();
    var promotion =
        storedPromotionUsage == null
            ? null
            : new PurchaseSnapshot.AppliedPromotionSnapshot(
                storedPromotionUsage.getPromotion().getPromotionId(),
                storedPromotionUsage
                    .getPromotion()
                    .getConfigurationSnapshot()
                    .configuration()
                    .code(),
                storedPromotionUsage.getDiscount().toPlainString());
    return new PurchaseSnapshot(
        1,
        lockedOrder.getId(),
        lockedOrder.getEventId(),
        lockedOrder.getBuyerSubject(),
        lockedOrder.getCustomerEmail(),
        storedConfiguration.sourceConfigVersion(),
        "1",
        decisionAt,
        lockedOrder.getExpiresAt(),
        lockedOrder.getCurrency(),
        storedConfiguration.eventTitle(),
        storedConfiguration.eventStartsAt(),
        storedConfiguration.eventEndsAt(),
        storedConfiguration.venueName(),
        lockedOrder.getSalesMode(),
        lockedOrder.getSectorId(),
        lockedOrder.getSubtotal().toPlainString(),
        lockedOrder.getDiscount().toPlainString(),
        lockedOrder.getTotal().toPlainString(),
        promotion,
        items);
  }

  @Override
  @Transactional
  @Retryable(includes = DataIntegrityViolationException.class, maxRetries = 1, delay = 0)
  public OrderView createOrder(
      String buyerSubject, UUID idempotencyKey, CreateOrderRequest request) {
    if (buyerSubject == null || buyerSubject.isBlank())
      throw new BusinessException(ErrorCode.UNAUTHENTICATED);
    if (idempotencyKey == null
        || request == null
        || request.getEventId() == null
        || request.getSelection() == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    if (reservationHoldDuration == null
        || reservationHoldDuration.isZero()
        || reservationHoldDuration.isNegative())
      throw new BusinessException(ErrorCode.INVALID_RESERVATION_DURATION);

    // Chỉ đặt timeout cho transaction này; commit/rollback sẽ trả lại cấu hình phiên.
    orderRepository.configureTransactionLockTimeout(reservationLockTimeout);

    String requestHash = calculateCreateOrderRequestHash(request);
    IdempotencyRequestId requestId = new IdempotencyRequestId();
    requestId.setActorSubject(buyerSubject);
    requestId.setOperation("CREATE_ORDER");
    requestId.setRequestKey(idempotencyKey);
    Optional<OrderView> replay = findReplayedOrder(requestId, requestHash);
    if (replay.isPresent()) return replay.get();

    // Nhiều đơn cùng đọc snapshot; consumer sửa cấu hình phải chờ transaction này.
    EventSalesSnapshot snapshot =
        eventSalesSnapshotRepository
            .findForBookingByEventId(request.getEventId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    replay = findReplayedOrder(requestId, requestHash);
    if (replay.isPresent()) return replay.get();

    PurchaseLimitId limitId = new PurchaseLimitId();
    limitId.setEventId(request.getEventId());
    limitId.setBuyerSubject(buyerSubject);
    PurchaseLimit limit = purchaseLimitRepository.findLockedById(limitId).orElse(null);
    if (limit == null) {
      limit = new PurchaseLimit();
      limit.setId(limitId);
      limit.setConfiguredLimit(snapshot.getPurchaseLimit());
      limit.setSourceConfigVersion(snapshot.getSourceConfigVersion());
      limit.setHeld(0);
      limit.setPurchased(0);
      // INSERT trước khóa inventory để PK phân xử hai đơn đầu của cùng buyer.
      // Trùng PK thì rollback và thử lại trong transaction mới.
      limit = purchaseLimitRepository.saveAndFlush(limit);
    }
    replay = findReplayedOrder(requestId, requestHash);
    if (replay.isPresent()) return replay.get();

    OrderSelection selection = request.getSelection();
    List<TicketTypeInventory> ticketInventories = List.of();
    SectorInventory standingSector = null;
    List<SeatInventory> seats = List.of();
    if (selection instanceof OrderSelection.QuantitySelection quantity) {
      ticketInventories =
          ticketTypeInventoryRepository.findLockedByTicketTypeIdInOrderByTicketTypeIdAsc(
              quantity.getItems().stream()
                  .map(OrderSelection.QuantityItem::getTicketTypeId)
                  .toList());
    } else if (selection instanceof OrderSelection.StandingSelection standing) {
      standingSector =
          sectorInventoryRepository.findLockedBySectorId(standing.getSectorId()).orElse(null);
    } else if (selection instanceof OrderSelection.SeatedSelection seated) {
      seats = seatInventoryRepository.findLockedBySeatIdInOrderBySeatIdAsc(seated.getSeatIds());
    } else {
      throw new BusinessException(ErrorCode.INVALID_SELECTION);
    }

    // Đọc lại sau khi chờ để request cùng key nhận order cũ trước khi kiểm hết vé.
    replay = findReplayedOrder(requestId, requestHash);
    if (replay.isPresent()) return replay.get();
    Instant decisionAt = orderRepository.currentDatabaseTime();
    if (snapshot.getEventStatus() != EventState.PUBLISHED
        || decisionAt.isBefore(snapshot.getSaleStartsAt())
        || !decisionAt.isBefore(snapshot.getSaleEndsAt()))
      throw new BusinessException(ErrorCode.EVENT_NOT_ON_SALE);

    SalesConfiguration configuration = snapshot.getConfigurationSnapshot();
    Order order = new Order();
    order.setEventId(request.getEventId());
    order.setBuyerSubject(buyerSubject);
    order.setSalesMode(snapshot.getSalesMode());
    if (selection instanceof OrderSelection.StandingSelection standing)
      order.setSectorId(standing.getSectorId());
    else if (selection instanceof OrderSelection.SeatedSelection seated)
      order.setSectorId(seated.getSectorId());
    order.setLifecycleState(OrderState.PENDING_PAYMENT);
    order.setCustomerEmail(request.getCustomerEmail());
    order.setCurrency(Currency.VND);
    order.setSourceConfigVersion(snapshot.getSourceConfigVersion());
    order.setCreatedAt(decisionAt);
    order.setUpdatedAt(decisionAt);
    order.setExpiresAt(decisionAt.plus(reservationHoldDuration));
    populateOrderItems(order, request, configuration);

    long quantity = order.getItems().stream().mapToLong(OrderItem::getQuantity).sum();
    if (quantity > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.INVALID_SELECTION);
    if ((long) limit.getHeld() + limit.getPurchased() + quantity > snapshot.getPurchaseLimit())
      throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED);

    order = orderRepository.save(order);
    Reservation reservation = new Reservation();
    reservation.setOrder(order);
    reservation.setEventId(order.getEventId());
    reservation.setBuyerSubject(buyerSubject);
    reservation.setState(ReservationState.HELD);
    reservation.setExpiresAt(order.getExpiresAt());
    reservation.setCreatedAt(decisionAt);
    reservation = reservationRepository.save(reservation);

    if (selection instanceof OrderSelection.QuantitySelection selected)
      reserveQuantityTickets(order, reservation, selected, ticketInventories);
    else if (selection instanceof OrderSelection.StandingSelection selected)
      reserveStandingTickets(order, reservation, selected, standingSector);
    else if (selection instanceof OrderSelection.SeatedSelection selected)
      reserveSeatedTickets(order, reservation, selected, seats);

    limit.setConfiguredLimit(snapshot.getPurchaseLimit());
    limit.setSourceConfigVersion(snapshot.getSourceConfigVersion());
    limit.setHeld(Math.toIntExact(limit.getHeld() + quantity));
    purchaseLimitRepository.save(limit);

    IdempotencyRequest receipt = new IdempotencyRequest();
    receipt.setId(requestId);
    receipt.setRequestHash(requestHash);
    receipt.setResourceId(order.getId());
    receipt.setResponseStatus(201);
    receipt.setCreatedAt(decisionAt);
    // Version null => INSERT; trùng key không ghi đè receipt của request khác.
    idempotencyRequestRepository.save(receipt);
    return orderMapper.toView(order);
  }

  private String calculateCreateOrderRequestHash(CreateOrderRequest request) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, exception);
    }
  }

  private Optional<OrderView> findReplayedOrder(
      IdempotencyRequestId requestId, String requestHash) {
    Optional<IdempotencyRequest> receipt = idempotencyRequestRepository.findById(requestId);
    if (receipt.isEmpty()) return Optional.empty();
    if (!requestHash.equals(receipt.get().getRequestHash()))
      throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    Order order =
        orderRepository
            .findById(receipt.get().getResourceId())
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    if (!requestId.getActorSubject().equals(order.getBuyerSubject()))
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    return Optional.of(orderMapper.toView(order));
  }

  private void populateOrderItems(
      Order order, CreateOrderRequest request, SalesConfiguration configuration) {
    OrderSelection selection = request.getSelection();
    if (configuration == null || !configuration.eventId().equals(order.getEventId()))
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    if (!configuration.mode().name().equals(selection.getMode()))
      throw new BusinessException(ErrorCode.INVALID_SELECTION);

    Map<UUID, SalesConfiguration.TicketTypeConfig> types = new HashMap<>();
    configuration.ticketTypes().forEach(type -> types.put(type.ticketTypeId(), type));
    Map<UUID, Integer> quantities = new TreeMap<>();
    Map<UUID, List<SeatSelectionSnapshot>> selectedSeats = new HashMap<>();

    if (selection instanceof OrderSelection.QuantitySelection quantity) {
      for (var item : quantity.getItems()) {
        var type = types.get(item.getTicketTypeId());
        if (type == null
            || type.sectorId() != null
            || item.getQuantity() <= 0
            || quantities.put(item.getTicketTypeId(), item.getQuantity()) != null)
          throw new BusinessException(ErrorCode.INVALID_SELECTION);
      }
    } else if (selection instanceof OrderSelection.StandingSelection standing) {
      boolean validSector =
          configuration.sectors().stream()
              .anyMatch(
                  sector ->
                      sector.sectorId().equals(standing.getSectorId())
                          && sector.kind() == SectorType.STANDING);
      if (!validSector) throw new BusinessException(ErrorCode.INVALID_SELECTION);
      for (var item : standing.getItems()) {
        var type = types.get(item.getTicketTypeId());
        if (type == null
            || !standing.getSectorId().equals(type.sectorId())
            || item.getQuantity() <= 0
            || quantities.put(item.getTicketTypeId(), item.getQuantity()) != null)
          throw new BusinessException(ErrorCode.INVALID_SELECTION);
      }
    } else if (selection instanceof OrderSelection.SeatedSelection seated) {
      boolean validSector =
          configuration.sectors().stream()
              .anyMatch(
                  sector ->
                      sector.sectorId().equals(seated.getSectorId())
                          && sector.kind() == SectorType.SEATED);
      if (!validSector) throw new BusinessException(ErrorCode.INVALID_SELECTION);
      Map<UUID, SalesConfiguration.SeatConfig> seats = new HashMap<>();
      configuration.seats().forEach(seat -> seats.put(seat.seatId(), seat));
      Set<UUID> seen = new HashSet<>();
      for (UUID seatId : seated.getSeatIds()) {
        var seat = seats.get(seatId);
        if (!seen.add(seatId) || seat == null || !seated.getSectorId().equals(seat.sectorId()))
          throw new BusinessException(ErrorCode.INVALID_SELECTION);
        var type = types.get(seat.ticketTypeId());
        if (type == null || !seated.getSectorId().equals(type.sectorId()))
          throw new BusinessException(ErrorCode.INVALID_SELECTION);
        if (seat.isHidden()) throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
        quantities.merge(seat.ticketTypeId(), 1, Integer::sum);
        selectedSeats
            .computeIfAbsent(seat.ticketTypeId(), ignored -> new ArrayList<>())
            .add(
                new SeatSelectionSnapshot(
                    seat.seatId(), seat.seatLabel(), seat.rowName(), seat.seatNumber()));
      }
    }

    if (quantities.isEmpty()) throw new BusinessException(ErrorCode.INVALID_SELECTION);
    BigDecimal subtotal = BigDecimal.ZERO;
    for (var entry : quantities.entrySet()) {
      var type = types.get(entry.getKey());
      if (type.currency() != Currency.VND) throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
      BigDecimal unitPrice = new BigDecimal(type.unitPrice());
      BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));
      subtotal = subtotal.add(lineSubtotal);
      if (lineSubtotal.precision() > 19 || subtotal.precision() > 19)
        throw new BusinessException(ErrorCode.ORDER_AMOUNT_EXCEEDED);
      OrderItem item = new OrderItem();
      item.setEventId(order.getEventId());
      item.setTicketTypeId(type.ticketTypeId());
      item.setSectorId(order.getSectorId());
      item.setSalesMode(order.getSalesMode());
      item.setTicketTypeName(type.name());
      item.setQuantity(entry.getValue());
      item.setUnitPrice(unitPrice);
      item.setLineSubtotal(lineSubtotal);
      item.setCurrency(Currency.VND);
      item.setSelectionSnapshot(
          new SelectionSnapshot(1, selectedSeats.getOrDefault(type.ticketTypeId(), List.of())));
      order.addItem(item);
    }
    if (subtotal.signum() <= 0) throw new BusinessException(ErrorCode.NON_POSITIVE_TOTAL);
    order.setSubtotal(subtotal);
    order.setDiscount(BigDecimal.ZERO);
    order.setTotal(subtotal);
  }

  private void reserveQuantityTickets(
      Order order,
      Reservation reservation,
      OrderSelection.QuantitySelection selection,
      List<TicketTypeInventory> lockedInventories) {
    if (lockedInventories.size() != selection.getItems().size())
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    Map<UUID, TicketTypeInventory> inventories = new HashMap<>();
    lockedInventories.forEach(inventory -> inventories.put(inventory.getTicketTypeId(), inventory));
    for (OrderItem item : order.getItems()) {
      TicketTypeInventory inventory = inventories.get(item.getTicketTypeId());
      if (inventory == null) throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
      if (!order.getEventId().equals(inventory.getEventId()))
        throw new BusinessException(ErrorCode.INVALID_SELECTION);
      if (inventory.getHeld()
          > inventory.getConfiguredCapacity() - inventory.getPurchased() - item.getQuantity())
        throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
      inventory.setHeld(inventory.getHeld() + item.getQuantity());
      addReservationAllocation(
          reservation, item, ResourceKind.TICKET_TYPE, item.getTicketTypeId(), item.getQuantity());
    }
    ticketTypeInventoryRepository.saveAll(lockedInventories);
  }

  private void reserveStandingTickets(
      Order order,
      Reservation reservation,
      OrderSelection.StandingSelection selection,
      SectorInventory lockedSector) {
    if (lockedSector == null) throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    if (!order.getEventId().equals(lockedSector.getEventId())
        || !selection.getSectorId().equals(lockedSector.getSectorId())
        || lockedSector.getSectorType() != SectorType.STANDING)
      throw new BusinessException(ErrorCode.INVALID_SELECTION);
    long quantity = order.getItems().stream().mapToLong(OrderItem::getQuantity).sum();
    // Mọi loại vé đứng cùng sector dùng một pool; chỉ tăng held một lần.
    if (lockedSector.getHeld()
        > lockedSector.getConfiguredCapacity() - lockedSector.getPurchased() - quantity)
      throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
    lockedSector.setHeld(lockedSector.getHeld() + quantity);
    for (OrderItem item : order.getItems())
      addReservationAllocation(
          reservation,
          item,
          ResourceKind.STANDING_SECTOR,
          lockedSector.getSectorId(),
          item.getQuantity());
    sectorInventoryRepository.save(lockedSector);
  }

  private void reserveSeatedTickets(
      Order order,
      Reservation reservation,
      OrderSelection.SeatedSelection selection,
      List<SeatInventory> lockedSeats) {
    if (lockedSeats.size() != selection.getSeatIds().size())
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    for (SeatInventory seat : lockedSeats) {
      if (!order.getEventId().equals(seat.getEventId())
          || !selection.getSectorId().equals(seat.getSectorId())
          || seat.getSectorType() != SectorType.SEATED)
        throw new BusinessException(ErrorCode.INVALID_SELECTION);
      if (Boolean.TRUE.equals(seat.getIsHidden())
          || seat.getState() != SeatState.AVAILABLE
          || seat.getCurrentReservationId() != null)
        throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
      OrderItem item =
          order.getItems().stream()
              .filter(
                  candidate ->
                      candidate.getTicketTypeId().equals(seat.getTicketTypeId())
                          && candidate.getSelectionSnapshot().seats().stream()
                              .anyMatch(selected -> selected.seatId().equals(seat.getSeatId())))
              .findFirst()
              .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SELECTION));

      // Đánh dấu đúng reservation đang giữ ghế và tăng generation cho lần giữ mới.
      seat.setState(SeatState.HELD);
      seat.setCurrentReservationId(reservation.getId());
      seat.setReservationGeneration(Math.addExact(seat.getReservationGeneration(), 1));
      OrderItemSeatId assignmentId = new OrderItemSeatId();
      assignmentId.setOrderItemId(item.getId());
      assignmentId.setSeatId(seat.getSeatId());
      OrderItemSeat assignment = new OrderItemSeat();
      assignment.setId(assignmentId);
      assignment.setOrderId(order.getId());
      assignment.setEventId(order.getEventId());
      assignment.setSectorId(seat.getSectorId());
      item.addSeatAssignment(assignment);
      addReservationAllocation(reservation, item, ResourceKind.SEAT, seat.getSeatId(), 1);
    }
    seatInventoryRepository.saveAll(lockedSeats);
  }

  private void addReservationAllocation(
      Reservation reservation,
      OrderItem item,
      ResourceKind resourceKind,
      UUID resourceId,
      int quantity) {
    ReservationAllocation allocation = new ReservationAllocation();
    allocation.setOrderId(reservation.getOrder().getId());
    allocation.setOrderItem(item);
    allocation.setEventId(reservation.getEventId());
    allocation.setResourceKind(resourceKind);
    allocation.setQuantity(quantity);
    allocation.setState(ReservationState.HELD);
    switch (resourceKind) {
      case TICKET_TYPE -> allocation.setTicketTypeId(resourceId);
      case STANDING_SECTOR -> allocation.setSectorId(resourceId);
      case SEAT -> allocation.setSeatId(resourceId);
    }
    reservation.addAllocation(allocation);
  }
}
