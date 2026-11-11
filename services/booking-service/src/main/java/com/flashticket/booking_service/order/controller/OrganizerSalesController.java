package com.flashticket.booking_service.order.controller;

import com.flashticket.booking_service.dto.response.SalesSummaryView;
import com.flashticket.booking_service.order.service.SalesSummaryCacheService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/events")
@RequiredArgsConstructor
public class OrganizerSalesController {
  private final SalesSummaryCacheService salesSummaryCacheService;

  @GetMapping("/{eventId}/sales-summary")
  public ResponseEntity<SalesSummaryView> getSalesSummary(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        salesSummaryCacheService.getSalesSummary(jwt.getSubject(), eventId, from, to));
  }
}
