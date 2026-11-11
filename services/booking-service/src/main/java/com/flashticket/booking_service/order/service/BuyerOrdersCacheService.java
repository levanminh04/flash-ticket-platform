package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.response.OrderPage;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.order.event.BuyerOrdersChangedEvent;
import com.flashticket.booking_service.redis.service.RedisService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuyerOrdersCacheService {
  private final OrderService orderService;
  private final RedisService redisService;
  private final JsonMapper jsonMapper;

  @Value("${booking.buyer-orders-cache.ttl:PT24H}")
  private Duration cacheTtl;

  @Value("${booking.buyer-orders-cache.key-prefix:flash-ticket:dev:booking:buyer-orders:v1:}")
  private String keyPrefix;

  public OrderPage getOrders(String buyerSubject, OrderState state, int page, int size) {
    // Để guard cũ của service phân loại lỗi identity, không tạo key từ subject thiếu.
    if (buyerSubject == null
        || buyerSubject.isBlank()
        || cacheTtl.isZero()
        || cacheTtl.isNegative()) return orderService.getOrders(buyerSubject, state, page, size);
    String buyerKey =
        keyPrefix
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buyerSubject.getBytes(StandardCharsets.UTF_8));
    String generationKey = buyerKey + ":generation";
    String dataKey = null;
    try {
      String generation = redisService.get(generationKey);
      if (generation == null) {
        // Request khác có thể vừa khởi tạo hoặc writer vừa invalidate.
        redisService.setIfAbsent(generationKey, UUID.randomUUID().toString());
        generation = redisService.get(generationKey);
      }
      if (generation != null) {
        dataKey =
            buyerKey
                + ":data:"
                + generation
                + ":state:"
                + (state == null ? "ALL" : state)
                + ":page:"
                + page
                + ":size:"
                + size;
        String cachedJson = redisService.get(dataKey);
        if (cachedJson != null) {
          try {
            OrderPage cachedPage = jsonMapper.readValue(cachedJson, OrderPage.class);
            if (cachedPage != null) return cachedPage;
          } catch (JacksonException exception) {
            log.warn(
                "Buyer orders cache operation=decode errorType={}",
                exception.getClass().getSimpleName());
          }
          // Chỉ xóa payload hỏng, giữ generation của buyer.
          redisService.clear(dataKey);
        }
      }
    } catch (DataAccessException exception) {
      dataKey = null;
      log.warn(
          "Buyer orders cache operation=read errorType={}", exception.getClass().getSimpleName());
    }
    // Service proxy kết thúc transaction RR trước Redis I/O; lỗi DB/nghiệp vụ đi ra ngoài.
    OrderPage orderPage = orderService.getOrders(buyerSubject, state, page, size);
    if (dataKey != null) {
      try {
        // Giữ token trước DB: reader cũ không ghi vào namespace mới sau invalidate.
        redisService.set(dataKey, jsonMapper.writeValueAsString(orderPage), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Buyer orders cache operation=write errorType={}",
            exception.getClass().getSimpleName());
      }
    }
    return orderPage;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
  public void invalidateBuyerOrdersCache(BuyerOrdersChangedEvent event) {
    String buyerKey =
        keyPrefix
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(event.getBuyerSubject().getBytes(StandardCharsets.UTF_8));
    try {
      // Đổi namespace mọi state/page/size sau commit, không quét key từng trang.
      redisService.set(buyerKey + ":generation", UUID.randomUUID().toString());
    } catch (DataAccessException exception) {
      // Redis lỗi không thể rollback DB đã commit; TTL payload là dự phòng.
      log.warn(
          "Buyer orders cache operation=invalidate errorType={}",
          exception.getClass().getSimpleName());
    }
  }
}
