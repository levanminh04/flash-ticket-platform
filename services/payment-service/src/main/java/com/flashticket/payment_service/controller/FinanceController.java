package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.dto.request.ReconciliationRequest;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.service.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Finance", description = "Tóm tắt tài chính, đối soát và chi trả sự kiện")
public class FinanceController {
  FinanceService financeService;

  @Operation(
      summary = "Tóm tắt tài chính sự kiện",
      description =
          "Dành cho event-service: doanh thu hợp lệ, đã hoàn, phí, thực nhận và đã có khoản thu hay chưa.")
  @GetMapping("/internal/v1/events/{eventId}/financial-summary")
  public EventFinancialSummary getFinancialSummary(
      @Parameter(description = "Mã sự kiện", required = true) @PathVariable UUID eventId) {
    // Trả JSON phẳng vì event-service đọc trực tiếp record theo hợp đồng nội bộ.
    return financeService.getFinancialSummary(eventId);
  }

  @Operation(
      summary = "Báo cáo đối soát sự kiện",
      description =
          "Admin xem mọi sự kiện; organizer chỉ xem sự kiện của mình. Gồm doanh thu hợp lệ, đã hoàn, phí, thực nhận và việc còn chờ.")
  @GetMapping("/api/v1/events/{eventId}/reconciliation")
  @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<EventReconciliationView> getReconciliation(
      @Parameter(description = "Mã sự kiện", required = true) @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean admin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    return ApiResponse.success(
        financeService.getReconciliation(eventId, jwt.getSubject(), admin),
        "Lấy báo cáo đối soát thành công");
  }

  @Operation(
      summary = "Xác nhận đối soát",
      description =
          "Chỉ khi sự kiện đã kết thúc, chưa hủy, không còn thanh toán hay hoàn tiền đang chờ; bắt buộc bằng chứng đối chiếu cổng thanh toán.")
  @PostMapping("/api/v1/admin/events/{eventId}/reconciliation")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<EventReconciliationView> confirmReconciliation(
      @Parameter(description = "Mã sự kiện", required = true) @PathVariable UUID eventId,
      @Valid @RequestBody ReconciliationRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    var response = financeService.confirmReconciliation(eventId, request, jwt.getSubject());
    log.info("[FinanceController] confirmReconciliation eventId={}", eventId);
    return ApiResponse.success(response, "Xác nhận đối soát thành công");
  }

  @Operation(
      summary = "Đánh dấu đã chi trả",
      description = "Đúng một lần sau khi đã xác nhận đối soát; số liệu đổi thì phải đối soát lại.")
  @PostMapping("/api/v1/admin/events/{eventId}/payout")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<EventReconciliationView> markEventPaid(
      @Parameter(description = "Mã sự kiện", required = true) @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt jwt) {
    var response = financeService.markEventPaid(eventId, jwt.getSubject());
    log.info("[FinanceController] markEventPaid eventId={}", eventId);
    return ApiResponse.success(response, "Đánh dấu đã chi trả thành công");
  }
}
