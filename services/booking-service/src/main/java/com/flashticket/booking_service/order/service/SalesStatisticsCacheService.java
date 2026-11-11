package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.response.*;
import com.flashticket.booking_service.enums.SalesGrouping;
import com.flashticket.booking_service.redis.service.RedisService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesStatisticsCacheService {
  private final OrderService orderService;
  private final RedisService redisService;
  private final JsonMapper jsonMapper;

  @Value("${booking.sales-statistics-cache.ttl:PT30S}")
  private Duration cacheTtl;

  @Value(
      "${booking.sales-statistics-cache.key-prefix:flash-ticket:dev:booking:sales-statistics:v1:}")
  private String keyPrefix;

  // Controller kiểm quyền trước cả cache hit; Redis I/O nằm ngoài transaction DB.
  public SalesStatisticsSummaryView getSalesStatisticsSummary(
      String organizerSubject, LocalDate from, LocalDate to) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return orderService.getSalesStatisticsSummary(organizerSubject, from, to);
    String scope =
        organizerSubject == null
            ? "system"
            : "organizer:"
                + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(organizerSubject.getBytes(StandardCharsets.UTF_8));
    String dataKey =
        keyPrefix
            + "summary:"
            + scope
            + ":"
            + (from == null ? "-" : from)
            + ":"
            + (to == null ? "-" : to);
    try {
      String cachedJson = redisService.get(dataKey);
      if (cachedJson != null) {
        try {
          var cached = jsonMapper.readValue(cachedJson, SalesStatisticsSummaryView.class);
          if (cached != null) return cached;
        } catch (JacksonException exception) {
          log.warn(
              "Sales statistics report=summary operation=decode errorType={}",
              exception.getClass().getSimpleName());
        }
        redisService.clear(dataKey);
      }
    } catch (DataAccessException exception) {
      dataKey = null;
      log.warn(
          "Sales statistics report=summary operation=read errorType={}",
          exception.getClass().getSimpleName());
    }
    // Lỗi nghiệp vụ đi ra ngoài, không ghi vào cache.
    var result = orderService.getSalesStatisticsSummary(organizerSubject, from, to);
    if (dataKey != null) {
      try {
        redisService.set(dataKey, jsonMapper.writeValueAsString(result), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Sales statistics report=summary operation=write errorType={}",
            exception.getClass().getSimpleName());
      }
    }
    return result;
  }

  // Controller kiểm quyền trước cả cache hit; Redis I/O nằm ngoài transaction DB.
  public SalesStatisticsChartView getSalesStatisticsChart(
      String organizerSubject, LocalDate from, LocalDate to, SalesGrouping groupBy) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return orderService.getSalesStatisticsChart(organizerSubject, from, to, groupBy);
    String scope =
        organizerSubject == null
            ? "system"
            : "organizer:"
                + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(organizerSubject.getBytes(StandardCharsets.UTF_8));
    String dataKey =
        keyPrefix
            + "chart:"
            + scope
            + ":"
            + (from == null ? "-" : from)
            + ":"
            + (to == null ? "-" : to)
            + ":"
            + groupBy;
    try {
      String cachedJson = redisService.get(dataKey);
      if (cachedJson != null) {
        try {
          var cached = jsonMapper.readValue(cachedJson, SalesStatisticsChartView.class);
          if (cached != null) return cached;
        } catch (JacksonException exception) {
          log.warn(
              "Sales statistics report=chart operation=decode errorType={}",
              exception.getClass().getSimpleName());
        }
        redisService.clear(dataKey);
      }
    } catch (DataAccessException exception) {
      dataKey = null;
      log.warn(
          "Sales statistics report=chart operation=read errorType={}",
          exception.getClass().getSimpleName());
    }
    // Lỗi nghiệp vụ đi ra ngoài, không ghi vào cache.
    var result = orderService.getSalesStatisticsChart(organizerSubject, from, to, groupBy);
    if (dataKey != null) {
      try {
        redisService.set(dataKey, jsonMapper.writeValueAsString(result), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Sales statistics report=chart operation=write errorType={}",
            exception.getClass().getSimpleName());
      }
    }
    return result;
  }

  // Controller kiểm quyền trước cả cache hit; Redis I/O nằm ngoài transaction DB.
  public EventSalesStatisticsPage getEventSalesStatistics(
      String organizerSubject,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      String sortBy,
      Sort.Direction direction) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return orderService.getEventSalesStatistics(
          organizerSubject, from, to, page, size, sortBy, direction);
    String scope =
        organizerSubject == null
            ? "system"
            : "organizer:"
                + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(organizerSubject.getBytes(StandardCharsets.UTF_8));
    String dataKey =
        keyPrefix
            + "events:"
            + scope
            + ":"
            + (from == null ? "-" : from)
            + ":"
            + (to == null ? "-" : to)
            + ":"
            + page
            + ":"
            + size
            + ":"
            + sortBy
            + ":"
            + direction;
    try {
      String cachedJson = redisService.get(dataKey);
      if (cachedJson != null) {
        try {
          var cached = jsonMapper.readValue(cachedJson, EventSalesStatisticsPage.class);
          if (cached != null) return cached;
        } catch (JacksonException exception) {
          log.warn(
              "Sales statistics report=events operation=decode errorType={}",
              exception.getClass().getSimpleName());
        }
        redisService.clear(dataKey);
      }
    } catch (DataAccessException exception) {
      dataKey = null;
      log.warn(
          "Sales statistics report=events operation=read errorType={}",
          exception.getClass().getSimpleName());
    }
    // Lỗi nghiệp vụ đi ra ngoài, không ghi vào cache.
    var result =
        orderService.getEventSalesStatistics(
            organizerSubject, from, to, page, size, sortBy, direction);
    if (dataKey != null) {
      try {
        redisService.set(dataKey, jsonMapper.writeValueAsString(result), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Sales statistics report=events operation=write errorType={}",
            exception.getClass().getSimpleName());
      }
    }
    return result;
  }

  // Controller kiểm quyền trước cả cache hit; Redis I/O nằm ngoài transaction DB.
  public SalesSummaryView getEventSalesStatistics(
      String organizerSubject, UUID eventId, LocalDate from, LocalDate to) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return orderService.getEventSalesStatistics(organizerSubject, eventId, from, to);
    String scope =
        organizerSubject == null
            ? "system"
            : "organizer:"
                + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(organizerSubject.getBytes(StandardCharsets.UTF_8));
    String dataKey =
        keyPrefix
            + "event:"
            + scope
            + ":"
            + (from == null ? "-" : from)
            + ":"
            + (to == null ? "-" : to)
            + ":"
            + eventId;
    try {
      String cachedJson = redisService.get(dataKey);
      if (cachedJson != null) {
        try {
          var cached = jsonMapper.readValue(cachedJson, SalesSummaryView.class);
          if (cached != null) return cached;
        } catch (JacksonException exception) {
          log.warn(
              "Sales statistics report=event operation=decode errorType={}",
              exception.getClass().getSimpleName());
        }
        redisService.clear(dataKey);
      }
    } catch (DataAccessException exception) {
      dataKey = null;
      log.warn(
          "Sales statistics report=event operation=read errorType={}",
          exception.getClass().getSimpleName());
    }
    // Lỗi nghiệp vụ đi ra ngoài, không ghi vào cache.
    var result = orderService.getEventSalesStatistics(organizerSubject, eventId, from, to);
    if (dataKey != null) {
      try {
        redisService.set(dataKey, jsonMapper.writeValueAsString(result), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Sales statistics report=event operation=write errorType={}",
            exception.getClass().getSimpleName());
      }
    }
    return result;
  }
}
