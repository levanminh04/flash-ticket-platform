package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.dto.request.ManualSettlementRequest;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.entity.enums.RefundState;
import com.flashticket.payment_service.service.RefundService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Refund", description = "Theo dõi và xử lý yêu cầu hoàn")
public class RefundController {
  RefundService refundService;

  @Operation(
      summary = "Danh sách yêu cầu hoàn",
      description =
          "Tra cứu mọi nguyên nhân hoàn theo trạng thái, mặc định FAILED; số trang bắt đầu từ 0.")
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/refunds")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PageResponse<RefundAdminView>> getRefunds(
      @Parameter(description = "Trạng thái yêu cầu hoàn, mặc định FAILED")
          @RequestParam(defaultValue = "FAILED")
          RefundState state,
      @Parameter(description = "Số trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Số yêu cầu trên mỗi trang") @RequestParam(defaultValue = "20")
          int size) {
    return ApiResponse.success(
        refundService.getRefunds(state, page, size), "Lấy danh sách yêu cầu hoàn thành công");
  }

  @Operation(
      summary = "Thử lại yêu cầu hoàn",
      description = "Chỉ chuyển FAILED về PENDING trên cùng yêu cầu hoàn; gọi lặp an toàn.")
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/refunds/{refundId}/retry")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<RefundAdminView> retryRefund(
      @Parameter(description = "Mã yêu cầu hoàn", required = true) @PathVariable UUID refundId) {
    var response = refundService.retryRefund(refundId);
    log.info("[RefundController] retryRefund refundId={}", refundId);
    return ApiResponse.success(response, "Thử lại yêu cầu hoàn thành công");
  }

  @Operation(
      summary = "Ghi nhận đã hoàn ngoài hệ thống",
      description = "Chỉ cho yêu cầu FAILED không còn lần gửi cổng dở; bắt buộc mã tham chiếu.")
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/refunds/{refundId}/manual-settlement")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<RefundAdminView> settleRefundManually(
      @Parameter(description = "Mã yêu cầu hoàn", required = true) @PathVariable UUID refundId,
      @Valid @RequestBody ManualSettlementRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    var response = refundService.settleRefundManually(refundId, request, jwt.getSubject());
    log.info("[RefundController] settleRefundManually refundId={}", refundId);
    return ApiResponse.success(response, "Ghi nhận hoàn ngoài hệ thống thành công");
  }

  @Operation(
      summary = "Tiến độ hoàn của sự kiện bị hủy",
      description = "Tra cứu số đơn đã chọn, đang chờ, đã hoàn và cần xử lý của sự kiện bị hủy.")
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/events/{eventId}/refund-progress")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EventRefundProgressView> getEventRefundProgress(
      @Parameter(description = "Mã sự kiện bị hủy", required = true) @PathVariable UUID eventId) {
    return ApiResponse.success(
        refundService.getEventRefundProgress(eventId), "Lấy tiến độ hoàn của sự kiện thành công");
  }
}
