package com.flashticket.booking_service.promotion.service.impl;

import com.flashticket.booking_service.availability.repository.EventSalesSnapshotRepository;
import com.flashticket.booking_service.dto.request.ApplyPromotionRequest;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.snapshot.PromotionConfig;
import com.flashticket.booking_service.entity.PromotionUsage;
import com.flashticket.booking_service.enums.*;
import com.flashticket.booking_service.exception.*;
import com.flashticket.booking_service.mapper.OrderMapper;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.promotion.repository.PromotionInventoryRepository;
import com.flashticket.booking_service.promotion.repository.PromotionUsageRepository;
import com.flashticket.booking_service.promotion.service.PromotionService;
import com.flashticket.booking_service.reservation.repository.ReservationRepository;
import java.math.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {
  private final PromotionInventoryRepository promotionInventoryRepository;
  private final PromotionUsageRepository promotionUsageRepository;
  private final OrderRepository orderRepository;
  private final EventSalesSnapshotRepository eventSalesSnapshotRepository;
  private final ReservationRepository reservationRepository;
  private final OrderMapper orderMapper;

  @Value("${booking.reservation.lock-timeout}")
  private String reservationLockTimeout;

  @Override
  @Transactional
  public OrderView applyPromotionToOrder(
      String buyerSubject, UUID orderId, ApplyPromotionRequest request) {
    if (buyerSubject == null || buyerSubject.isBlank())
      throw new BusinessException(ErrorCode.UNAUTHENTICATED);
    if (orderId == null
        || request == null
        || request.getCode() == null
        || request.getCode().isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    String requestedPromotionCode = request.getCode().trim().toUpperCase(Locale.ROOT);
    orderRepository.configureTransactionLockTimeout(reservationLockTimeout);
    // Chỉ đọc ID để chưa đưa Order cũ vào persistence context trước khi chờ khóa.
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
    if (!lockedOrder.getExpiresAt().equals(storedReservation.getExpiresAt())
        || !eventId.equals(storedReservation.getEventId())
        || !buyerSubject.equals(storedReservation.getBuyerSubject()))
      throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
    if (lockedOrder.getPaymentFrozenAt() != null)
      throw new BusinessException(ErrorCode.PRICE_FROZEN);
    var activeStates = List.of(PromotionUsageState.HELD, PromotionUsageState.USED);
    var storedUsage =
        promotionUsageRepository.findByReservationIdAndStateIn(
            storedReservation.getId(), activeStates);
    if (storedUsage.isPresent()) {
      var usage = storedUsage.get();
      if (usage.getState() == PromotionUsageState.HELD
          && requestedPromotionCode.equals(
              usage
                  .getPromotion()
                  .getConfigurationSnapshot()
                  .configuration()
                  .code()
                  .trim()
                  .toUpperCase(Locale.ROOT))) return orderMapper.toView(lockedOrder);
      throw new BusinessException(ErrorCode.ORDER_ALREADY_HAS_PROMOTION);
    }
    var matchingPromotions =
        storedSnapshot.getConfigurationSnapshot().promotions().stream()
            .filter(p -> requestedPromotionCode.equals(p.code().trim().toUpperCase(Locale.ROOT)))
            .toList();
    if (matchingPromotions.isEmpty()) throw new BusinessException(ErrorCode.PROMOTION_NOT_FOUND);
    if (matchingPromotions.size() != 1)
      throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
    var promotionConfiguration = matchingPromotions.getFirst();
    var lockedPromotion =
        promotionInventoryRepository
            .findLockedByPromotionId(promotionConfiguration.promotionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
    if (!eventId.equals(lockedPromotion.getEventId())
        || !Objects.equals(
            lockedPromotion.getSourceConfigVersion(), storedSnapshot.getSourceConfigVersion())
        || !promotionConfiguration.equals(
            lockedPromotion.getConfigurationSnapshot().configuration()))
      throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
    // Request có thể chờ lượt cuối của mã: kiểm lại đồng hồ sau khóa promotion.
    decisionAt = orderRepository.currentDatabaseTime();
    if (!decisionAt.isBefore(lockedOrder.getExpiresAt()))
      throw new BusinessException(ErrorCode.ORDER_EXPIRED);
    if (decisionAt.isBefore(lockedPromotion.getStartsAt())
        || !decisionAt.isBefore(lockedPromotion.getEndsAt()))
      throw new BusinessException(ErrorCode.PROMOTION_OUTSIDE_WINDOW);
    if (lockedPromotion.getUsed() > lockedPromotion.getConfiguredLimit()
        || lockedPromotion.getHeld()
            >= lockedPromotion.getConfiguredLimit() - lockedPromotion.getUsed())
      throw new BusinessException(ErrorCode.PROMOTION_EXHAUSTED);
    if (promotionUsageRepository.existsByPromotionPromotionIdAndBuyerSubjectAndStateIn(
        lockedPromotion.getPromotionId(), buyerSubject, activeStates))
      throw new BusinessException(ErrorCode.PROMOTION_ALREADY_USED);
    var discount = calculatePromotionDiscount(lockedOrder.getSubtotal(), promotionConfiguration);
    var total = lockedOrder.getSubtotal().subtract(discount);
    if (total.signum() <= 0) throw new BusinessException(ErrorCode.NON_POSITIVE_TOTAL);
    var usage = new PromotionUsage();
    usage.setPromotion(lockedPromotion);
    usage.setEventId(eventId);
    usage.setReservation(storedReservation);
    usage.setBuyerSubject(buyerSubject);
    usage.setGeneration(1L);
    usage.setState(PromotionUsageState.HELD);
    usage.setDiscount(discount);
    usage.setCurrency(lockedOrder.getCurrency());
    usage.setExpiresAt(lockedOrder.getExpiresAt());
    lockedPromotion.setHeld(lockedPromotion.getHeld() + 1);
    lockedOrder.setDiscount(discount);
    lockedOrder.setTotal(total);
    lockedOrder.setUpdatedAt(decisionAt);
    // Lượt mã và tiền của đơn cùng commit; lỗi DB phải đi ra ngoài để rollback cả bộ.
    promotionUsageRepository.save(usage);
    promotionInventoryRepository.save(lockedPromotion);
    orderRepository.save(lockedOrder);
    return orderMapper.toView(lockedOrder);
  }

  private BigDecimal calculatePromotionDiscount(
      BigDecimal subtotal, PromotionConfig promotionConfiguration) {
    return switch (promotionConfiguration.discount()) {
      case PromotionConfig.FixedDiscount fixed -> new BigDecimal(fixed.amount());
      case PromotionConfig.PercentDiscount percent ->
          subtotal
              .multiply(BigDecimal.valueOf(percent.percent()))
              .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    };
  }
}
