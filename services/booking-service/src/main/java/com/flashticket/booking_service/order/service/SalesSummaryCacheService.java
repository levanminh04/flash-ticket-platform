package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.response.SalesSummaryView;
import com.flashticket.booking_service.redis.service.RedisService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesSummaryCacheService {
  private final OrderService orderService;
  private final RedisService redisService;
  private final JsonMapper jsonMapper;

  // Chỉ dùng TTL: số liệu có thể cũ tối đa bằng TTL, response có asOf để FE hiển thị.
  @Value("${booking.sales-summary-cache.ttl:PT30S}")
  private Duration cacheTtl;

  @Value("${booking.sales-summary-cache.key-prefix:flash-ticket:dev:booking:sales-summary:v1:}")
  private String keyPrefix;

  public SalesSummaryView getSalesSummary(
      String organizerSubject, UUID eventId, LocalDate from, LocalDate to) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return orderService.getSalesSummary(organizerSubject, eventId, from, to);

    // Key có organizer: chỉ ghi sau khi service đã kiểm ownership => organizer khác không đọc được.
    String dataKey =
        keyPrefix
            + eventId
            + ":"
            + organizerSubject
            + ":"
            + (from == null ? "-" : from)
            + ":"
            + (to == null ? "-" : to);
    try {
      String cachedJson = redisService.get(dataKey);
      if (cachedJson != null) {
        try {
          SalesSummaryView cachedSummary = jsonMapper.readValue(cachedJson, SalesSummaryView.class);
          if (cachedSummary != null) return cachedSummary;
        } catch (JacksonException exception) {
          log.warn(
              "Sales summary cache operation=decode eventId={} errorType={}",
              eventId,
              exception.getClass().getSimpleName());
        }
        // JSON hỏng thì xóa để lần sau nạp lại từ DB.
        redisService.clear(dataKey);
      }
    } catch (DataAccessException exception) {
      // Redis đọc/xóa lỗi thì dùng DB và bỏ qua bước ghi cache.
      dataKey = null;
      log.warn(
          "Sales summary cache operation=read eventId={} errorType={}",
          eventId,
          exception.getClass().getSimpleName());
    }

    // Gọi qua proxy để transaction DB kết thúc trước khi ghi cache; lỗi nghiệp vụ đi thẳng ra
    // ngoài nên 404/400 không bao giờ được ghi vào cache.
    SalesSummaryView salesSummary =
        orderService.getSalesSummary(organizerSubject, eventId, from, to);
    if (dataKey != null) {
      try {
        redisService.set(dataKey, jsonMapper.writeValueAsString(salesSummary), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Sales summary cache operation=write eventId={} errorType={}",
            eventId,
            exception.getClass().getSimpleName());
      }
    }
    return salesSummary;
  }
}
