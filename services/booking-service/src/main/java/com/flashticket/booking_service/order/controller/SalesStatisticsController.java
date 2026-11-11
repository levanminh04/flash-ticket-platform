package com.flashticket.booking_service.order.controller;

import com.flashticket.booking_service.dto.response.*;
import com.flashticket.booking_service.enums.SalesGrouping;
import com.flashticket.booking_service.order.service.OrderService;
import com.flashticket.booking_service.order.service.SalesStatisticsCacheService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SalesStatisticsController {
  private final SalesStatisticsCacheService salesStatisticsCacheService;
  private final OrderService orderService;

  @GetMapping("/organizers/{organizerSubject}/sales/summary")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('ORGANIZER') and #organizerSubject == authentication.name)")
  public ResponseEntity<SalesStatisticsSummaryView> getOrganizerSalesSummary(
      @PathVariable String organizerSubject,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        salesStatisticsCacheService.getSalesStatisticsSummary(organizerSubject, from, to));
  }

  @GetMapping("/organizers/{organizerSubject}/sales/chart")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('ORGANIZER') and #organizerSubject == authentication.name)")
  public ResponseEntity<SalesStatisticsChartView> getOrganizerSalesChart(
      @PathVariable String organizerSubject,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "DAY") SalesGrouping groupBy) {
    return ResponseEntity.ok(
        salesStatisticsCacheService.getSalesStatisticsChart(organizerSubject, from, to, groupBy));
  }

  @GetMapping("/organizers/{organizerSubject}/events/sales-statistics")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('ORGANIZER') and #organizerSubject == authentication.name)")
  public ResponseEntity<EventSalesStatisticsPage> getOrganizerEventSalesStatistics(
      @PathVariable String organizerSubject,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "soldRevenue") String sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
    return ResponseEntity.ok(
        salesStatisticsCacheService.getEventSalesStatistics(
            organizerSubject, from, to, page, size, sortBy, direction));
  }

  @GetMapping("/events/{eventId}/sales-statistics")
  @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
  public ResponseEntity<SalesSummaryView> getEventSalesStatistics(
      @PathVariable UUID eventId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Authentication authentication) {
    boolean admin =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    // Owner được kiểm cả cache hit; không nhận role/subject từ request.
    String organizerSubject =
        orderService.getSalesEventOrganizerSubject(eventId, authentication.getName(), admin);
    return ResponseEntity.ok(
        salesStatisticsCacheService.getEventSalesStatistics(organizerSubject, eventId, from, to));
  }
}
