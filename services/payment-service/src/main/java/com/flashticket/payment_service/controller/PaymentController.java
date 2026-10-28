package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.dto.request.CreatePaymentRequest;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Payment", description = "Thanh toán VNPay, IPN và tra cứu trạng thái")
public class PaymentController {
  PaymentService paymentService;

  @Operation(
      summary = "Tạo thanh toán VNPay",
      description = "Tạo hoặc sử dụng lại thanh toán PENDING của đơn thuộc người mua.")
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/payments")
  @PreAuthorize("hasRole('BUYER')")
  public ApiResponse<PaymentStart> createPayment(
      @Valid @RequestBody CreatePaymentRequest request,
      HttpServletRequest httpRequest,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(
        paymentService.createPayment(request, httpRequest, jwt.getSubject()),
        "Payment created successfully");
  }

  @Operation(
      summary = "Xử lý trả về từ VNPay",
      description = "Xác minh chữ ký và chuyển về app; trạng thái được cập nhật bởi IPN.")
  @GetMapping("/payments/vnpay/return")
  public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
    var redirectUri = paymentService.buildAppReturnUri(params);
    return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
  }

  @Operation(summary = "Nhận IPN từ VNPay")
  @GetMapping(value = "/payments/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> handleIPN(@RequestParam Map<String, String> params) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(paymentService.handleIPN(params));
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/orders/{orderId}/payment")
  @PreAuthorize("hasRole('BUYER')")
  public ApiResponse<PaymentView> getPayment(
      @Parameter(description = "Mã đơn hàng", required = true) @PathVariable UUID orderId,
      @Parameter(description = "Mã yêu cầu khởi tạo thanh toán, nếu có")
          @RequestParam(required = false)
          UUID requestId,
      @AuthenticationPrincipal Jwt jwt) {
    var response = paymentService.getPayment(orderId, requestId, jwt.getSubject());
    log.info("[PaymentController] getPayment - Retrieved payment for order: {}", orderId);
    return ApiResponse.success(response, "Lấy thông tin thanh toán thành công");
  }
}
