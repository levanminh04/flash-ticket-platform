package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.config.BookingChartReplicaHealth;
import com.flashticket.booking_service.dto.response.SalesSummaryView;
import com.flashticket.booking_service.redis.service.RedisService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Slf4j
public class SalesSummaryCacheService {
  private final OrderService orderService;
  private final RedisService redisService;
  private final JsonMapper jsonMapper;
  private final ObjectProvider<TransactionTemplate> reportTransactionProvider;
  private final ObjectProvider<BookingChartReplicaHealth> replicaHealthProvider;
  private final MeterRegistry metrics;

  @Value("${booking.sales-event-report-replica.enabled:false}")
  private boolean eventReportReplicaEnabled;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Shares Spring-managed RedisService, JsonMapper, and MeterRegistry without exposing them.")
  public SalesSummaryCacheService(
      OrderService orderService,
      RedisService redisService,
      JsonMapper jsonMapper,
      @Qualifier("bookingSalesReportReadTransactionTemplate")
          ObjectProvider<TransactionTemplate> reportTransactionProvider,
      @Qualifier("bookingChartReplicaHealth")
          ObjectProvider<BookingChartReplicaHealth> replicaHealthProvider,
      MeterRegistry metrics) {
    this.orderService = orderService;
    this.redisService = redisService;
    this.jsonMapper = jsonMapper;
    this.reportTransactionProvider = reportTransactionProvider;
    this.replicaHealthProvider = replicaHealthProvider;
    this.metrics = metrics;
  }

  // TTL chỉ là một phần độ cũ dữ liệu, còn phải tính snapshot và tuổi mẫu reader.
  @Value("${booking.sales-summary-cache.ttl:PT30S}")
  private Duration cacheTtl;

  @Value("${booking.sales-summary-cache.key-prefix:flash-ticket:dev:booking:sales-summary:v1:}")
  private String keyPrefix;

  private SalesSummaryView loadSalesSummary(
      String organizerSubject, UUID eventId, LocalDate from, LocalDate to) {
    if (!eventReportReplicaEnabled
        || organizerSubject == null
        || TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isSynchronizationActive()) {
      return orderService.getSalesSummary(organizerSubject, eventId, from, to);
    }
    var transaction = reportTransactionProvider.getIfAvailable();
    var health = transaction == null ? null : replicaHealthProvider.getIfAvailable();
    if (health == null || !health.tryBeginRead()) {
      return orderService.getSalesSummary(organizerSubject, eventId, from, to);
    }
    try {
      metrics
          .counter("booking.sales.report.source", "report", "legacy", "source", "reader")
          .increment();
      // Giữ overload legacy để không đổi khoảng ngày và điều kiện owner.
      return transaction.execute(
          status -> orderService.getSalesSummary(organizerSubject, eventId, from, to));
    } catch (RuntimeException failure) {
      health.invalidate();
      metrics.counter("booking.sales.report.replica.attempt.error", "report", "legacy").increment();
      throw failure;
    } finally {
      health.finishRead();
    }
  }

  public SalesSummaryView getSalesSummary(
      String organizerSubject, UUID eventId, LocalDate from, LocalDate to) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return loadSalesSummary(organizerSubject, eventId, from, to);

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

    // Loader tự mở transaction thì hoàn tất trước ghi cache; ambient transaction vẫn do caller giữ.
    // Lỗi nghiệp vụ đi ra ngoài, không ghi 404/400 vào cache.
    SalesSummaryView salesSummary = loadSalesSummary(organizerSubject, eventId, from, to);
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
