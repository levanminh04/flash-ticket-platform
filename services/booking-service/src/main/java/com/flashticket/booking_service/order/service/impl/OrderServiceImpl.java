package com.flashticket.booking_service.order.service.impl;

import com.flashticket.booking_service.availability.repository.*;
import com.flashticket.booking_service.dto.message.AcceptPayment;
import com.flashticket.booking_service.dto.message.BookingIssuanceResult;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.OrderCompleted;
import com.flashticket.booking_service.dto.message.PaymentDecision;
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
import com.flashticket.booking_service.mapper.OutboxMessageMapper;
import com.flashticket.booking_service.messaging.repository.InboxMessageRepository;
import com.flashticket.booking_service.messaging.repository.OutboxMessageRepository;
import com.flashticket.booking_service.order.repository.IdempotencyRequestRepository;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import com.flashticket.booking_service.promotion.repository.PromotionInventoryRepository;
import com.flashticket.booking_service.promotion.repository.PromotionUsageRepository;
import com.flashticket.booking_service.reservation.repository.ReservationRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
  private final InboxMessageRepository inboxMessageRepository;
  private final OutboxMessageRepository outboxMessageRepository;
  private final OutboxMessageMapper outboxMessageMapper;
  private final PromotionInventoryRepository promotionInventoryRepository;

  @Value("${booking.reservation.hold-duration}")
  private Duration reservationHoldDuration;

  @Value("${booking.reservation.lock-timeout}")
  private String reservationLockTimeout;

  @Override
  @Transactional
  public void processBookingIssuanceResult(Envelope<BookingIssuanceResult> message) {
    if (message == null
        || !validator.validate(message).isEmpty()
        || !message.aggregateId().equals(message.data().orderId())
        || !("ticket.issued.v1".equals(message.messageType())
                && message.data().state() == BookingIssuanceResult.State.COMPLETED
            || "booking.issuance-failed.v1".equals(message.messageType())
                && message.data().state() == BookingIssuanceResult.State.FAILED))
      throw new BusinessException(ErrorCode.INVALID_MESSAGE);
    String payloadHash = calculateIssuanceResultHash(message);
    orderRepository.configureTransactionLockTimeout(reservationLockTimeout);
    UUID eventId =
        orderRepository
            .findEventIdById(message.data().orderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ISSUANCE_RESULT_NOT_READY));
    var storedSnapshot =
        eventSalesSnapshotRepository
            .findForBookingByEventId(eventId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ISSUANCE_RESULT_NOT_READY));
    var lockedOrder =
        orderRepository
            .findLockedById(message.data().orderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ISSUANCE_RESULT_NOT_READY));
    var receiptId = new InboxMessageId();
    receiptId.setConsumerName("booking.issuance-result");
    receiptId.setMessageId(message.messageId());
    // Hai queue có thể cùng xử lý order; message khác vừa commit trong lúc chờ khóa.
    var receipt = inboxMessageRepository.findById(receiptId);
    if (receipt.isPresent()) {
      if (!payloadHash.equals(receipt.get().getPayloadHash()))
        throw new BusinessException(ErrorCode.MESSAGE_ID_CONFLICT);
      return;
    }
    validateIssuanceResult(lockedOrder, message);
    OrderState previousState = lockedOrder.getLifecycleState();
    boolean cancelled =
        previousState == OrderState.CANCELLED
            || storedSnapshot.getEventStatus() == EventState.CANCELLED;
    boolean completed = message.data().state() == BookingIssuanceResult.State.COMPLETED;
    if (!completed && previousState != OrderState.ISSUANCE_FAILED) {
      // Trả purchased/used, kể cả event đóng bán/hủy; không chạy lại khi FAILED đã commit.
      releaseResourcesAfterIssuanceFailure(lockedOrder);
      lockedOrder.setLifecycleState(cancelled ? OrderState.CANCELLED : OrderState.ISSUANCE_FAILED);
    } else if (completed && previousState == OrderState.ISSUING) {
      lockedOrder.setLifecycleState(cancelled ? OrderState.CANCELLED : OrderState.COMPLETED);
    }
    Instant processedAt = orderRepository.currentDatabaseTime();
    if (previousState != lockedOrder.getLifecycleState()) {
      lockedOrder.setUpdatedAt(processedAt);
      orderRepository.save(lockedOrder);
    }
    if (completed && !cancelled) enqueueOrderCompleted(message, processedAt);
    persistIssuanceResultReceipt(message, payloadHash, processedAt);
    // Không gọi broker ở đây: order, counter, inbox và outbox commit cùng nhau.
  }

  private String calculateIssuanceResultHash(Envelope<BookingIssuanceResult> message) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(message)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, exception);
    }
  }

  private void validateIssuanceResult(Order lockedOrder, Envelope<BookingIssuanceResult> message) {
    if (lockedOrder.getLifecycleState() == OrderState.PENDING_PAYMENT
        && lockedOrder.getAcceptedChargeId() == null)
      throw new BusinessException(ErrorCode.ISSUANCE_RESULT_NOT_READY);
    var frozen = lockedOrder.getFrozenPurchaseSnapshot();
    if (lockedOrder.getAcceptedChargeId() == null
        || lockedOrder.getAcceptedAt() == null
        || !lockedOrder.getAcceptedChargeId().equals(message.data().chargeId())
        || frozen == null
        || !validator.validate(frozen).isEmpty()
        || !lockedOrder.getId().equals(frozen.orderId())
        || !lockedOrder.getEventId().equals(frozen.eventId())
        || !lockedOrder.getBuyerSubject().equals(frozen.buyerSubject())
        || !Objects.equals(lockedOrder.getPaymentFrozenAt(), frozen.frozenAt())
        || !Objects.equals(
            lockedOrder.getPaymentSnapshotVersion(), Long.valueOf(frozen.sourceOrderVersion()))
        || !message.aggregateVersion().equals(frozen.sourceOrderVersion())
        || !List.of(
                OrderState.ISSUING,
                OrderState.COMPLETED,
                OrderState.ISSUANCE_FAILED,
                OrderState.CANCELLED)
            .contains(lockedOrder.getLifecycleState()))
      throw new BusinessException(ErrorCode.ISSUANCE_RESULT_CONFLICT);
    boolean completed = message.data().state() == BookingIssuanceResult.State.COMPLETED;
    if (completed
            && (lockedOrder.getLifecycleState() == OrderState.ISSUANCE_FAILED
                || frozen.items().stream().mapToLong(PurchaseSnapshot.Item::quantity).sum()
                    != message.data().ticketCount())
        || !completed && lockedOrder.getLifecycleState() == OrderState.COMPLETED)
      throw new BusinessException(ErrorCode.ISSUANCE_RESULT_CONFLICT);
    // Không lưu/đối chiếu issuanceId; Payment chịu trách nhiệm kiểm đợt phát vé của Saga.
  }

  private void releaseResourcesAfterIssuanceFailure(Order lockedOrder) {
    var storedReservation =
        reservationRepository
            .findByOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
    var allocations = storedReservation.getAllocations();
    if (!lockedOrder.getEventId().equals(storedReservation.getEventId())
        || !lockedOrder.getBuyerSubject().equals(storedReservation.getBuyerSubject())
        || !lockedOrder.getExpiresAt().equals(storedReservation.getExpiresAt())
        || !List.of(ReservationState.COMMITTED, ReservationState.RELEASED)
            .contains(storedReservation.getState())
        || allocations.isEmpty()
        || allocations.stream()
            .anyMatch(
                a ->
                    a.getState() != storedReservation.getState()
                        || !lockedOrder.getId().equals(a.getOrderId())
                        || !lockedOrder.getEventId().equals(a.getEventId())
                        || !lockedOrder.getId().equals(a.getOrderItem().getOrder().getId())
                        || a.getQuantity() == null
                        || a.getQuantity() <= 0))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    var frozen = lockedOrder.getFrozenPurchaseSnapshot();
    if (lockedOrder.getItems().size() != frozen.items().size()
        || lockedOrder.getSalesMode() != frozen.mode()
        || !Objects.equals(lockedOrder.getSectorId(), frozen.sectorId()))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    for (var storedItem : lockedOrder.getItems()) {
      var frozenItem =
          frozen.items().stream()
              .filter(i -> i.orderItemId().equals(storedItem.getId()))
              .findFirst()
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      long allocated =
          allocations.stream()
              .filter(a -> a.getOrderItem().getId().equals(storedItem.getId()))
              .mapToLong(ReservationAllocation::getQuantity)
              .sum();
      if (allocated != storedItem.getQuantity()
          || allocated != frozenItem.quantity()
          || !storedItem.getEventId().equals(lockedOrder.getEventId())
          || !storedItem.getTicketTypeId().equals(frozenItem.ticketTypeId())
          || !Objects.equals(storedItem.getSectorId(), frozenItem.sectorId())
          || storedItem.getSelectionSnapshot() == null
          || storedItem.getSelectionSnapshot().seats() == null
          || !new HashSet<>(storedItem.getSelectionSnapshot().seats())
              .equals(new HashSet<>(frozenItem.seats())))
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    }
    if (allocations.stream()
        .anyMatch(
            a ->
                lockedOrder.getItems().stream()
                    .noneMatch(i -> i.getId().equals(a.getOrderItem().getId()))))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    var limitId = new PurchaseLimitId();
    limitId.setEventId(lockedOrder.getEventId());
    limitId.setBuyerSubject(lockedOrder.getBuyerSubject());
    var lockedLimit =
        purchaseLimitRepository
            .findLockedById(limitId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
    List<TicketTypeInventory> lockedTypes = List.of();
    SectorInventory lockedSector = null;
    List<SeatInventory> lockedSeats = List.of();
    boolean releaseInventory = storedReservation.getState() == ReservationState.COMMITTED;
    if (releaseInventory) {
      if (lockedOrder.getSalesMode() == com.flashticket.booking_service.enums.SalesMode.QUANTITY) {
        lockedTypes =
            ticketTypeInventoryRepository.findLockedByTicketTypeIdInOrderByTicketTypeIdAsc(
                allocations.stream()
                    .map(ReservationAllocation::getTicketTypeId)
                    .distinct()
                    .toList());
      } else if (allocations.stream()
          .allMatch(a -> a.getResourceKind() == ResourceKind.STANDING_SECTOR)) {
        lockedSector =
            sectorInventoryRepository
                .findLockedBySectorId(lockedOrder.getSectorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      } else if (allocations.stream().allMatch(a -> a.getResourceKind() == ResourceKind.SEAT)) {
        lockedSeats =
            seatInventoryRepository.findLockedBySeatIdInOrderBySeatIdAsc(
                allocations.stream().map(ReservationAllocation::getSeatId).toList());
      } else throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    }
    var storedUsage =
        promotionUsageRepository
            .findByReservationIdAndStateIn(
                storedReservation.getId(),
                List.of(
                    PromotionUsageState.HELD,
                    PromotionUsageState.USED,
                    PromotionUsageState.RELEASED))
            .orElse(null);
    PromotionInventory lockedPromotion = null;
    if (storedUsage != null)
      lockedPromotion =
          promotionInventoryRepository
              .findLockedByPromotionId(storedUsage.getPromotion().getPromotionId())
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
    if ((storedUsage == null) != (frozen.promotion() == null))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    if (releaseInventory) {
      releasePurchasedLimit(lockedOrder, storedReservation, lockedLimit);
      if (lockedOrder.getSalesMode() == com.flashticket.booking_service.enums.SalesMode.QUANTITY)
        releasePurchasedQuantityInventory(lockedOrder, storedReservation, lockedTypes);
      else if (lockedSector != null)
        releasePurchasedStandingInventory(lockedOrder, storedReservation, lockedSector);
      else releasePurchasedSeatInventory(lockedOrder, storedReservation, lockedSeats);
      allocations.forEach(a -> a.setState(ReservationState.RELEASED));
      storedReservation.setState(ReservationState.RELEASED);
      reservationRepository.save(storedReservation);
    }
    releaseUsedPromotion(lockedOrder, storedUsage, lockedPromotion);
  }

  private void releasePurchasedQuantityInventory(
      Order lockedOrder,
      Reservation storedReservation,
      List<TicketTypeInventory> lockedInventories) {
    var quantityByTicketTypeId = new HashMap<UUID, Long>();
    for (var allocation : storedReservation.getAllocations()) {
      if (allocation.getResourceKind() != ResourceKind.TICKET_TYPE
          || !allocation.getOrderItem().getTicketTypeId().equals(allocation.getTicketTypeId()))
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      quantityByTicketTypeId.merge(
          allocation.getTicketTypeId(), (long) allocation.getQuantity(), Long::sum);
    }
    if (lockedInventories.size() != quantityByTicketTypeId.size())
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    for (var lockedInventory : lockedInventories) {
      long quantity = quantityByTicketTypeId.getOrDefault(lockedInventory.getTicketTypeId(), 0L);
      if (quantity <= 0
          || !lockedOrder.getEventId().equals(lockedInventory.getEventId())
          || lockedInventory.getPurchased() < quantity)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      lockedInventory.setPurchased(lockedInventory.getPurchased() - quantity);
    }
    ticketTypeInventoryRepository.saveAll(lockedInventories);
  }

  private void releasePurchasedStandingInventory(
      Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector) {
    if (!lockedOrder.getEventId().equals(lockedSector.getEventId())
        || !lockedOrder.getSectorId().equals(lockedSector.getSectorId())
        || lockedSector.getSectorType() != SectorType.STANDING
        || storedReservation.getAllocations().stream()
            .anyMatch(
                a ->
                    a.getResourceKind() != ResourceKind.STANDING_SECTOR
                        || !lockedSector.getSectorId().equals(a.getSectorId())
                        || !lockedSector.getSectorId().equals(a.getOrderItem().getSectorId())))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    long quantity =
        storedReservation.getAllocations().stream()
            .mapToLong(ReservationAllocation::getQuantity)
            .sum();
    if (lockedSector.getPurchased() < quantity)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    lockedSector.setPurchased(lockedSector.getPurchased() - quantity);
    sectorInventoryRepository.save(lockedSector);
  }

  private void releasePurchasedSeatInventory(
      Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats) {
    var allocations = storedReservation.getAllocations();
    if (lockedSeats.size() != allocations.size())
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    for (var lockedSeat : lockedSeats) {
      var allocation =
          allocations.stream()
              .filter(a -> lockedSeat.getSeatId().equals(a.getSeatId()))
              .findFirst()
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      if (allocation.getResourceKind() != ResourceKind.SEAT
          || allocation.getQuantity() != 1
          || lockedSeat.getState() != SeatState.PURCHASED
          || !storedReservation.getId().equals(lockedSeat.getCurrentReservationId())
          || !lockedOrder.getEventId().equals(lockedSeat.getEventId())
          || !lockedOrder.getSectorId().equals(lockedSeat.getSectorId())
          || lockedSeat.getSectorType() != SectorType.SEATED
          || !allocation.getOrderItem().getTicketTypeId().equals(lockedSeat.getTicketTypeId())
          || allocation.getOrderItem().getSelectionSnapshot().seats().stream()
              .noneMatch(s -> s.seatId().equals(lockedSeat.getSeatId()))
          || lockedSeat.getReservationGeneration() <= 0)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      // Chỉ trả ghế còn thuộc reservation này; không sửa ghế đã được R2 giữ lại.
      lockedSeat.setState(
          Boolean.TRUE.equals(lockedSeat.getIsHidden())
              ? SeatState.UNAVAILABLE
              : SeatState.AVAILABLE);
      lockedSeat.setCurrentReservationId(null);
    }
    seatInventoryRepository.saveAll(lockedSeats);
  }

  private void releasePurchasedLimit(
      Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit) {
    int quantity =
        Math.toIntExact(
            storedReservation.getAllocations().stream()
                .mapToLong(ReservationAllocation::getQuantity)
                .sum());
    if (!lockedOrder.getEventId().equals(lockedPurchaseLimit.getId().getEventId())
        || !lockedOrder.getBuyerSubject().equals(lockedPurchaseLimit.getId().getBuyerSubject())
        || lockedPurchaseLimit.getPurchased() < quantity)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    lockedPurchaseLimit.setPurchased(lockedPurchaseLimit.getPurchased() - quantity);
    purchaseLimitRepository.save(lockedPurchaseLimit);
  }

  private void releaseUsedPromotion(
      Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion) {
    if (storedUsage == null) return;
    var promotion = lockedOrder.getFrozenPurchaseSnapshot().promotion();
    if (promotion == null
        || !promotion.promotionId().equals(lockedPromotion.getPromotionId())
        || !lockedOrder.getEventId().equals(lockedPromotion.getEventId())
        || !lockedOrder.getEventId().equals(storedUsage.getEventId())
        || !lockedOrder.getBuyerSubject().equals(storedUsage.getBuyerSubject())
        || !lockedOrder.getExpiresAt().equals(storedUsage.getExpiresAt())
        || lockedOrder.getCurrency() != storedUsage.getCurrency()
        || lockedOrder.getDiscount().compareTo(storedUsage.getDiscount()) != 0
        || !List.of(PromotionUsageState.USED, PromotionUsageState.RELEASED)
            .contains(storedUsage.getState()))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    if (storedUsage.getState() == PromotionUsageState.RELEASED) return;
    if (lockedPromotion.getUsed() < 1)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    lockedPromotion.setUsed(lockedPromotion.getUsed() - 1);
    storedUsage.setState(PromotionUsageState.RELEASED);
    promotionUsageRepository.save(storedUsage);
    promotionInventoryRepository.save(lockedPromotion);
  }

  private void persistIssuanceResultReceipt(
      Envelope<BookingIssuanceResult> message, String payloadHash, Instant processedAt) {
    var id = new InboxMessageId();
    id.setConsumerName("booking.issuance-result");
    id.setMessageId(message.messageId());
    var receipt = new InboxMessage();
    receipt.setId(id);
    receipt.setPayloadHash(payloadHash);
    receipt.setProcessedAt(processedAt);
    // Version null => INSERT; trùng messageId phải rollback, không ghi đè receipt.
    inboxMessageRepository.save(receipt);
  }

  private void enqueueOrderCompleted(Envelope<BookingIssuanceResult> message, Instant occurredAt) {
    UUID responseId =
        UUID.nameUUIDFromBytes(
            ("booking.order-completed:" + message.messageId()).getBytes(StandardCharsets.UTF_8));
    var data = message.data();
    var response =
        new Envelope<>(
            responseId,
            "booking.order-completed.v1",
            1,
            data.orderId(),
            message.aggregateVersion(),
            occurredAt,
            message.correlationId(),
            message.messageId(),
            message.traceparent(),
            new OrderCompleted(
                data.orderId(), data.chargeId(), data.issuanceId(), data.ticketCount()));
    outboxMessageRepository.save(outboxMessageMapper.toEntity(response));
  }

  @Override
  @Transactional
  public PaymentDecision acceptPayment(Envelope<AcceptPayment> message) {
    if (message == null
        || !validator.validate(message).isEmpty()
        || !"booking.accept-payment.v1".equals(message.messageType())
        || !message.aggregateId().equals(message.data().orderId())
        || !message.aggregateVersion().equals(message.data().sourceOrderVersion()))
      throw new BusinessException(ErrorCode.INVALID_MESSAGE);
    String payloadHash = calculateAcceptPaymentHash(message);
    orderRepository.configureTransactionLockTimeout(reservationLockTimeout);
    UUID eventId =
        orderRepository
            .findEventIdById(message.data().orderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    var storedSnapshot =
        eventSalesSnapshotRepository
            .findForBookingByEventId(eventId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    var lockedOrder =
        orderRepository
            .findLockedById(message.data().orderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    var receiptId = new InboxMessageId();
    receiptId.setConsumerName("booking.accept-payment");
    receiptId.setMessageId(message.messageId());
    // Message khác có thể đã commit trong lúc chờ khóa order.
    var receipt = inboxMessageRepository.findById(receiptId);
    if (receipt.isPresent()) {
      if (!payloadHash.equals(receipt.get().getPayloadHash()))
        throw new BusinessException(ErrorCode.MESSAGE_ID_CONFLICT);
      if (receipt.get().getResponsePayload() == null)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      var replayed =
          objectMapper.treeToValue(receipt.get().getResponsePayload(), PaymentDecision.class);
      if (!validator.validate(replayed).isEmpty()
          || !lockedOrder.getId().equals(replayed.orderId())
          || !message.data().chargeId().equals(replayed.chargeId()))
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      return replayed;
    }
    validatePaymentSnapshot(lockedOrder, message.data());
    PaymentDecision decision;
    if (lockedOrder.getAcceptedChargeId() != null) {
      // Charge đã nhận phải được phân xử trước deadline, không đổi ACCEPTED thành LATE.
      if (lockedOrder.getAcceptedAt() == null)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      boolean sameCharge = lockedOrder.getAcceptedChargeId().equals(message.data().chargeId());
      decision =
          new PaymentDecision(
              lockedOrder.getId(),
              message.data().chargeId(),
              sameCharge ? lockedOrder.getAcceptedAt() : orderRepository.currentDatabaseTime(),
              sameCharge ? PaymentDecision.Outcome.ACCEPTED : PaymentDecision.Outcome.DUPLICATE,
              lockedOrder.getAcceptedChargeId());
    } else {
      var storedReservation =
          reservationRepository
              .findByOrderId(lockedOrder.getId())
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      decision =
          settlePendingPayment(lockedOrder, storedReservation, storedSnapshot, message.data());
    }
    persistPaymentDecision(message, payloadHash, decision);
    return decision;
  }

  private String calculateAcceptPaymentHash(Envelope<AcceptPayment> message) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(message)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, exception);
    }
  }

  private void validatePaymentSnapshot(Order lockedOrder, AcceptPayment requestedPayment) {
    var frozen = lockedOrder.getFrozenPurchaseSnapshot();
    if (frozen == null
        || !validator.validate(frozen).isEmpty()
        || lockedOrder.getPaymentFrozenAt() == null
        || !lockedOrder.getId().equals(frozen.orderId())
        || !lockedOrder.getEventId().equals(frozen.eventId())
        || !lockedOrder.getBuyerSubject().equals(frozen.buyerSubject())
        || !lockedOrder.getExpiresAt().equals(frozen.expiresAt())
        || !lockedOrder.getPaymentFrozenAt().equals(frozen.frozenAt())
        || !Objects.equals(
            lockedOrder.getPaymentSnapshotVersion(), Long.valueOf(frozen.sourceOrderVersion()))
        || !requestedPayment.sourceOrderVersion().equals(frozen.sourceOrderVersion())
        || requestedPayment.currency() != frozen.currency()
        || lockedOrder.getCurrency() != frozen.currency()
        || new BigDecimal(requestedPayment.amount()).signum() <= 0
        || new BigDecimal(requestedPayment.amount()).compareTo(lockedOrder.getTotal()) != 0
        || new BigDecimal(frozen.total()).compareTo(lockedOrder.getTotal()) != 0
        || new BigDecimal(frozen.subtotal()).compareTo(lockedOrder.getSubtotal()) != 0
        || new BigDecimal(frozen.discount()).compareTo(lockedOrder.getDiscount()) != 0)
      throw new BusinessException(ErrorCode.PAYMENT_SNAPSHOT_MISMATCH);
  }

  private PaymentDecision settlePendingPayment(
      Order lockedOrder,
      Reservation storedReservation,
      EventSalesSnapshot storedSnapshot,
      AcceptPayment requestedPayment) {
    if (!storedReservation.getEventId().equals(lockedOrder.getEventId())
        || !storedReservation.getBuyerSubject().equals(lockedOrder.getBuyerSubject())
        || !storedReservation.getExpiresAt().equals(lockedOrder.getExpiresAt())
        || storedReservation.getState() == ReservationState.COMMITTED
        || !List.of(OrderState.PENDING_PAYMENT, OrderState.EXPIRED, OrderState.CANCELLED)
            .contains(lockedOrder.getLifecycleState()))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    var allocations = storedReservation.getAllocations();
    if (lockedOrder.getItems().size() != lockedOrder.getFrozenPurchaseSnapshot().items().size()
        || lockedOrder.getSalesMode() != lockedOrder.getFrozenPurchaseSnapshot().mode()
        || !Objects.equals(
            lockedOrder.getSectorId(), lockedOrder.getFrozenPurchaseSnapshot().sectorId()))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    if (allocations.isEmpty()
        || allocations.stream()
            .anyMatch(
                a ->
                    !lockedOrder.getId().equals(a.getOrderId())
                        || !lockedOrder.getEventId().equals(a.getEventId())
                        || !lockedOrder.getId().equals(a.getOrderItem().getOrder().getId())
                        || a.getQuantity() == null
                        || a.getQuantity() <= 0
                        || a.getState() != storedReservation.getState()))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    // Allocation là phần giữ của chính đơn này; tổng phải khớp từng item đã chốt.
    for (var storedItem : lockedOrder.getItems()) {
      long allocated =
          allocations.stream()
              .filter(a -> a.getOrderItem().getId().equals(storedItem.getId()))
              .mapToLong(ReservationAllocation::getQuantity)
              .sum();
      var frozenItem =
          lockedOrder.getFrozenPurchaseSnapshot().items().stream()
              .filter(i -> i.orderItemId().equals(storedItem.getId()))
              .findFirst()
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      if (allocated != storedItem.getQuantity()
          || allocated != frozenItem.quantity()
          || !storedItem.getTicketTypeId().equals(frozenItem.ticketTypeId())
          || !lockedOrder.getEventId().equals(storedItem.getEventId())
          || !Objects.equals(storedItem.getSectorId(), frozenItem.sectorId())
          || storedItem.getSelectionSnapshot() == null
          || storedItem.getSelectionSnapshot().seats() == null
          || !new HashSet<>(storedItem.getSelectionSnapshot().seats())
              .equals(new HashSet<>(frozenItem.seats()))
          || storedItem.getUnitPrice().compareTo(new BigDecimal(frozenItem.unitPrice())) != 0
          || storedItem.getLineSubtotal().compareTo(new BigDecimal(frozenItem.lineSubtotal())) != 0)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    }
    if (allocations.stream()
        .anyMatch(
            a ->
                lockedOrder.getItems().stream()
                    .noneMatch(i -> i.getId().equals(a.getOrderItem().getId()))))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    var limitId = new PurchaseLimitId();
    limitId.setEventId(lockedOrder.getEventId());
    limitId.setBuyerSubject(lockedOrder.getBuyerSubject());
    var lockedLimit =
        purchaseLimitRepository
            .findLockedById(limitId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
    List<TicketTypeInventory> lockedTypes = List.of();
    SectorInventory lockedSector = null;
    List<SeatInventory> lockedSeats = List.of();
    if (storedReservation.getState() == ReservationState.HELD) {
      if (lockedOrder.getSalesMode() == com.flashticket.booking_service.enums.SalesMode.QUANTITY) {
        if (allocations.stream().anyMatch(a -> a.getResourceKind() != ResourceKind.TICKET_TYPE))
          throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
        lockedTypes =
            ticketTypeInventoryRepository.findLockedByTicketTypeIdInOrderByTicketTypeIdAsc(
                allocations.stream()
                    .map(ReservationAllocation::getTicketTypeId)
                    .distinct()
                    .toList());
      } else if (allocations.stream()
          .allMatch(a -> a.getResourceKind() == ResourceKind.STANDING_SECTOR)) {
        lockedSector =
            sectorInventoryRepository
                .findLockedBySectorId(lockedOrder.getSectorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      } else if (allocations.stream().allMatch(a -> a.getResourceKind() == ResourceKind.SEAT)) {
        lockedSeats =
            seatInventoryRepository.findLockedBySeatIdInOrderBySeatIdAsc(
                allocations.stream().map(ReservationAllocation::getSeatId).toList());
      } else throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    }
    var storedUsage =
        promotionUsageRepository
            .findByReservationIdAndStateIn(
                storedReservation.getId(),
                List.of(
                    PromotionUsageState.HELD,
                    PromotionUsageState.USED,
                    PromotionUsageState.RELEASED))
            .orElse(null);
    PromotionInventory lockedPromotion = null;
    if (storedUsage != null) {
      lockedPromotion =
          promotionInventoryRepository
              .findLockedByPromotionId(storedUsage.getPromotion().getPromotionId())
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      if (!storedUsage.getEventId().equals(lockedOrder.getEventId())
          || !storedUsage.getBuyerSubject().equals(lockedOrder.getBuyerSubject())
          || storedUsage.getDiscount().compareTo(lockedOrder.getDiscount()) != 0
          || storedUsage.getCurrency() != lockedOrder.getCurrency()
          || !storedUsage.getExpiresAt().equals(lockedOrder.getExpiresAt()))
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    }
    if ((storedUsage == null) != (lockedOrder.getFrozenPurchaseSnapshot().promotion() == null))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    // Đồng hồ phải đọc sau mọi bước có thể chờ khóa inventory/promotion.
    Instant decisionAt = orderRepository.currentDatabaseTime();
    boolean cancelled =
        storedSnapshot.getEventStatus() == EventState.CANCELLED
            || lockedOrder.getLifecycleState() == OrderState.CANCELLED;
    boolean late =
        lockedOrder.getLifecycleState() == OrderState.EXPIRED
            || !decisionAt.isBefore(lockedOrder.getExpiresAt());
    boolean accepted = !cancelled && !late;
    if (accepted && storedReservation.getState() != ReservationState.HELD)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    ReservationState targetState =
        accepted ? ReservationState.COMMITTED : ReservationState.RELEASED;
    if (storedReservation.getState() == ReservationState.HELD) {
      updatePurchaseLimitForPaymentDecision(
          lockedOrder, storedReservation, lockedLimit, targetState);
      if (lockedOrder.getSalesMode() == com.flashticket.booking_service.enums.SalesMode.QUANTITY)
        updateQuantityInventoryForPaymentDecision(
            lockedOrder, storedReservation, lockedTypes, targetState);
      else if (lockedSector != null)
        updateStandingInventoryForPaymentDecision(
            lockedOrder, storedReservation, lockedSector, targetState);
      else
        updateSeatedInventoryForPaymentDecision(
            lockedOrder, storedReservation, lockedSeats, targetState);
      updatePromotionUsageForPaymentDecision(
          lockedOrder, storedUsage, lockedPromotion, targetState);
      allocations.forEach(a -> a.setState(targetState));
      storedReservation.setState(targetState);
      reservationRepository.save(storedReservation);
    } else if (storedUsage != null && storedUsage.getState() != PromotionUsageState.RELEASED)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    if (accepted) {
      lockedOrder.setLifecycleState(OrderState.ISSUING);
      lockedOrder.setAcceptedChargeId(requestedPayment.chargeId());
      lockedOrder.setAcceptedAt(decisionAt);
    } else if (lockedOrder.getLifecycleState() == OrderState.PENDING_PAYMENT) {
      lockedOrder.setLifecycleState(cancelled ? OrderState.CANCELLED : OrderState.EXPIRED);
    }
    lockedOrder.setUpdatedAt(decisionAt);
    orderRepository.save(lockedOrder);
    return new PaymentDecision(
        lockedOrder.getId(),
        requestedPayment.chargeId(),
        decisionAt,
        accepted
            ? PaymentDecision.Outcome.ACCEPTED
            : cancelled ? PaymentDecision.Outcome.CANCELLED : PaymentDecision.Outcome.LATE,
        accepted ? requestedPayment.chargeId() : null);
  }

  private void updateQuantityInventoryForPaymentDecision(
      Order lockedOrder,
      Reservation storedReservation,
      List<TicketTypeInventory> lockedInventories,
      ReservationState targetState) {
    var quantityByType = new HashMap<UUID, Long>();
    for (var allocation : storedReservation.getAllocations()) {
      if (allocation.getResourceKind() != ResourceKind.TICKET_TYPE
          || !allocation.getOrderItem().getTicketTypeId().equals(allocation.getTicketTypeId()))
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      quantityByType.merge(
          allocation.getTicketTypeId(), (long) allocation.getQuantity(), Long::sum);
    }
    if (lockedInventories.size() != quantityByType.size())
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    for (var lockedInventory : lockedInventories) {
      long quantity = quantityByType.getOrDefault(lockedInventory.getTicketTypeId(), 0L);
      if (quantity <= 0
          || !lockedOrder.getEventId().equals(lockedInventory.getEventId())
          || lockedInventory.getHeld() < quantity)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      lockedInventory.setHeld(lockedInventory.getHeld() - quantity);
      if (targetState == ReservationState.COMMITTED)
        lockedInventory.setPurchased(Math.addExact(lockedInventory.getPurchased(), quantity));
    }
    ticketTypeInventoryRepository.saveAll(lockedInventories);
  }

  private void updateStandingInventoryForPaymentDecision(
      Order lockedOrder,
      Reservation storedReservation,
      SectorInventory lockedSector,
      ReservationState targetState) {
    if (!lockedOrder.getEventId().equals(lockedSector.getEventId())
        || lockedSector.getSectorType() != SectorType.STANDING
        || storedReservation.getAllocations().stream()
            .anyMatch(
                a ->
                    a.getResourceKind() != ResourceKind.STANDING_SECTOR
                        || !lockedSector.getSectorId().equals(a.getSectorId())))
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    long quantity =
        storedReservation.getAllocations().stream()
            .mapToLong(ReservationAllocation::getQuantity)
            .sum();
    if (lockedSector.getHeld() < quantity)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    lockedSector.setHeld(lockedSector.getHeld() - quantity);
    if (targetState == ReservationState.COMMITTED)
      lockedSector.setPurchased(Math.addExact(lockedSector.getPurchased(), quantity));
    sectorInventoryRepository.save(lockedSector);
  }

  private void updateSeatedInventoryForPaymentDecision(
      Order lockedOrder,
      Reservation storedReservation,
      List<SeatInventory> lockedSeats,
      ReservationState targetState) {
    var allocations = storedReservation.getAllocations();
    if (lockedSeats.size() != allocations.size())
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    for (var lockedSeat : lockedSeats) {
      var allocation =
          allocations.stream()
              .filter(a -> lockedSeat.getSeatId().equals(a.getSeatId()))
              .findFirst()
              .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT));
      if (allocation.getResourceKind() != ResourceKind.SEAT
          || allocation.getQuantity() != 1
          || lockedSeat.getState() != SeatState.HELD
          || !storedReservation.getId().equals(lockedSeat.getCurrentReservationId())
          || !lockedOrder.getEventId().equals(lockedSeat.getEventId())
          || !lockedOrder.getSectorId().equals(lockedSeat.getSectorId())
          || !allocation.getOrderItem().getTicketTypeId().equals(lockedSeat.getTicketTypeId())
          || allocation.getOrderItem().getSelectionSnapshot().seats().stream()
              .noneMatch(s -> s.seatId().equals(lockedSeat.getSeatId()))
          || lockedSeat.getReservationGeneration() <= 0)
        throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
      if (targetState == ReservationState.COMMITTED) {
        if (Boolean.TRUE.equals(lockedSeat.getIsHidden()))
          throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
        lockedSeat.setState(SeatState.PURCHASED);
      } else {
        lockedSeat.setState(
            Boolean.TRUE.equals(lockedSeat.getIsHidden())
                ? SeatState.UNAVAILABLE
                : SeatState.AVAILABLE);
        lockedSeat.setCurrentReservationId(null);
      }
    }
    seatInventoryRepository.saveAll(lockedSeats);
  }

  private void updatePurchaseLimitForPaymentDecision(
      Order lockedOrder,
      Reservation storedReservation,
      PurchaseLimit lockedPurchaseLimit,
      ReservationState targetState) {
    int quantity =
        Math.toIntExact(
            storedReservation.getAllocations().stream()
                .mapToLong(ReservationAllocation::getQuantity)
                .sum());
    if (!lockedOrder.getEventId().equals(lockedPurchaseLimit.getId().getEventId())
        || !lockedOrder.getBuyerSubject().equals(lockedPurchaseLimit.getId().getBuyerSubject())
        || lockedPurchaseLimit.getHeld() < quantity)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    lockedPurchaseLimit.setHeld(lockedPurchaseLimit.getHeld() - quantity);
    if (targetState == ReservationState.COMMITTED)
      lockedPurchaseLimit.setPurchased(Math.addExact(lockedPurchaseLimit.getPurchased(), quantity));
    purchaseLimitRepository.save(lockedPurchaseLimit);
  }

  private void updatePromotionUsageForPaymentDecision(
      Order lockedOrder,
      PromotionUsage storedUsage,
      PromotionInventory lockedPromotion,
      ReservationState targetState) {
    if (storedUsage == null) return;
    var promotion = lockedOrder.getFrozenPurchaseSnapshot().promotion();
    if (promotion == null
        || !promotion.promotionId().equals(lockedPromotion.getPromotionId())
        || !lockedOrder.getEventId().equals(lockedPromotion.getEventId())
        || storedUsage.getState() != PromotionUsageState.HELD
        || lockedPromotion.getHeld() < 1)
      throw new BusinessException(ErrorCode.RESERVATION_DATA_CONFLICT);
    lockedPromotion.setHeld(lockedPromotion.getHeld() - 1);
    if (targetState == ReservationState.COMMITTED) {
      lockedPromotion.setUsed(Math.addExact(lockedPromotion.getUsed(), 1));
      storedUsage.setState(PromotionUsageState.USED);
    } else storedUsage.setState(PromotionUsageState.RELEASED);
    promotionUsageRepository.save(storedUsage);
    promotionInventoryRepository.save(lockedPromotion);
  }

  private void persistPaymentDecision(
      Envelope<AcceptPayment> message, String payloadHash, PaymentDecision decision) {
    if (!validator.validate(decision).isEmpty())
      throw new BusinessException(ErrorCode.INVALID_MESSAGE);
    var id = new InboxMessageId();
    id.setConsumerName("booking.accept-payment");
    id.setMessageId(message.messageId());
    var receipt = new InboxMessage();
    receipt.setId(id);
    receipt.setPayloadHash(payloadHash);
    receipt.setProcessedAt(orderRepository.currentDatabaseTime());
    receipt.setResponsePayload(objectMapper.valueToTree(decision));
    // Version null: ID gán trước vẫn INSERT; PK chặn receipt cạnh tranh khác order.
    inboxMessageRepository.save(receipt);
    UUID responseId =
        UUID.nameUUIDFromBytes(
            ("booking.payment-decision:" + message.messageId()).getBytes(StandardCharsets.UTF_8));
    var envelope =
        new Envelope<>(
            responseId,
            "booking.payment-decision.v1",
            1,
            decision.orderId(),
            message.data().sourceOrderVersion(),
            decision.decisionAt(),
            message.correlationId(),
            message.messageId(),
            message.traceparent(),
            decision);
    outboxMessageRepository.save(outboxMessageMapper.toEntity(envelope));
  }

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
