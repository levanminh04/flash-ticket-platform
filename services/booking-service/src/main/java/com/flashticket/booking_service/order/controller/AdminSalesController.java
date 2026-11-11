package com.flashticket.booking_service.order.controller;

import com.flashticket.booking_service.dto.response.*;
import com.flashticket.booking_service.enums.SalesGrouping;
import com.flashticket.booking_service.order.service.SalesStatisticsCacheService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSalesController {
  private final SalesStatisticsCacheService salesStatisticsCacheService;

  @GetMapping("/sales/summary")
  public ResponseEntity<SalesStatisticsSummaryView> getSystemSalesSummary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(salesStatisticsCacheService.getSalesStatisticsSummary(null, from, to));
  }

  @GetMapping("/sales/chart")
  public ResponseEntity<SalesStatisticsChartView> getSystemSalesChart(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "DAY") SalesGrouping groupBy) {
    return ResponseEntity.ok(
        salesStatisticsCacheService.getSalesStatisticsChart(null, from, to, groupBy));
  }

  @GetMapping("/events/sales-statistics")
  public ResponseEntity<EventSalesStatisticsPage> getSystemEventSalesStatistics(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "soldRevenue") String sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
    return ResponseEntity.ok(
        salesStatisticsCacheService.getEventSalesStatistics(
            null, from, to, page, size, sortBy, direction));
  }
}
