package com.flashticket.payment_service.service.payment;

import com.flashticket.payment_service.config.VNPayConfig;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.message.PaymentProvider;
import com.flashticket.payment_service.dto.message.StartPaymentResult;
import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import com.flashticket.payment_service.dto.request.CreatePaymentRequest;
import com.flashticket.payment_service.dto.response.PaymentStart;
import com.flashticket.payment_service.dto.response.PaymentView;
import com.flashticket.payment_service.dto.response.StartRequestState;
import com.flashticket.payment_service.dto.response.StartRequestView;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.mapper.*;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {
  private static final String START_CONSUMER = "payment.start-requested.v1";
  OrderPaymentSnapshotRepository snapshots;
  PaymentAttemptRepository attempts;
  PaymentConfirmationRepository confirmations;
  RefundRepository refunds;
  InboxMessageRepository inbox;
  PaymentViewMapper views;
  PaymentStartMapper starts;
  RefundMapper refundMapper;
  MessageJsonMapper messages;
  VNPayConfig vnPayConfig;
  ChargeRepository charges;
  ContractHashService hashes;

  @Transactional
  public PaymentStart createPayment(
      CreatePaymentRequest request, HttpServletRequest httpRequest, String buyerSubject) {
    if (buyerSubject == null || buyerSubject.isBlank())
      throw new PaymentException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
    var snapshot =
        snapshots
            .findForUpdateByOrderId(request.orderId())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_READY));
    if (!buyerSubject.equals(snapshot.getBuyerSubject()))
      throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
    if (!snapshot.getExpiresAt().isAfter(Instant.now()))
      throw new PaymentException(PaymentErrorCode.PAYMENT_EXPIRED);
    if (!"VND".equals(snapshot.getCurrency()) || !"VND".equals(vnPayConfig.getCurrencyCode()))
      throw new PaymentException(PaymentErrorCode.PAYMENT_STATE_INVALID);
    if (confirmations.existsById(snapshot.getOrderId()))
      throw new PaymentException(PaymentErrorCode.PAYMENT_STATE_INVALID);

    var existing = attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(snapshot.getOrderId());
    var payment = existing.orElse(null);
    if (payment != null
        && (payment.getState() == PaymentAttemptState.SUCCEEDED
            || payment.getState() == PaymentAttemptState.UNKNOWN))
      throw new PaymentException(PaymentErrorCode.PAYMENT_STATE_INVALID);
    if (payment == null || payment.getState() != PaymentAttemptState.PENDING) {
      payment =
          PaymentAttempt.pending(
              UUID.randomUUID(),
              snapshot.getOrderId(),
              "VNPAY",
              vnPayConfig.getTmnCode(),
              snapshot.getExpectedAmount(),
              snapshot.getCurrency(),
              Instant.now(),
              UUID.randomUUID(),
              hashes.requestHash(snapshot.getOrderId(), PaymentProvider.VNPAY, buyerSubject));
    }
    String clientIp = resolveClientIp(request, httpRequest);
    payment.setPaymentUrl(buildVNPayUrl(snapshot, payment, clientIp));
    var saved = attempts.save(payment);
    log.info("Payment created: orderId={}, attemptId={}", snapshot.getOrderId(), saved.getId());
    return starts.toDto(saved, snapshot.getExpiresAt());
  }

  @Transactional
  public String handleIPN(Map<String, String> params) {
    if (!VNPayUtil.verifySecureHash(params, vnPayConfig.getHashSecret()))
      return "{\"RspCode\":\"97\",\"Message\":\"Invalid signature\"}";
    UUID attemptId;
    if (params.get("vnp_TxnRef") == null)
      return "{\"RspCode\":\"01\",\"Message\":\"Order not found\"}";
    try {
      attemptId = UUID.fromString(params.get("vnp_TxnRef"));
    } catch (IllegalArgumentException ex) {
      return "{\"RspCode\":\"01\",\"Message\":\"Order not found\"}";
    }
    var payment = attempts.findForUpdateById(attemptId).orElse(null);
    if (payment == null || !"VNPAY".equals(payment.getProvider()))
      return "{\"RspCode\":\"01\",\"Message\":\"Order not found\"}";
    if (!payment.getMerchantAccount().equals(params.get("vnp_TmnCode"))
        || !vnPayConfig.getTmnCode().equals(params.get("vnp_TmnCode")))
      return "{\"RspCode\":\"97\",\"Message\":\"Invalid merchant\"}";
    if (payment.getState() != PaymentAttemptState.PENDING
        && payment.getState() != PaymentAttemptState.UNKNOWN)
      return "{\"RspCode\":\"02\",\"Message\":\"Order already confirmed\"}";
    if (params.get("vnp_Amount") == null)
      return "{\"RspCode\":\"04\",\"Message\":\"Amount invalid\"}";
    try {
      var received = new BigDecimal(params.get("vnp_Amount"));
      if (payment.getExpectedAmount().multiply(BigDecimal.valueOf(100)).compareTo(received) != 0)
        return "{\"RspCode\":\"04\",\"Message\":\"Amount invalid\"}";
    } catch (NumberFormatException ex) {
      return "{\"RspCode\":\"04\",\"Message\":\"Amount invalid\"}";
    }
    String responseCode = params.get("vnp_ResponseCode");
    String transactionStatus = params.get("vnp_TransactionStatus");
    if (responseCode == null
        || !responseCode.matches("[0-9]{2}")
        || transactionStatus == null
        || !transactionStatus.matches("[0-9]{2}"))
      return "{\"RspCode\":\"99\",\"Message\":\"Invalid response\"}";
    boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);
    boolean failed =
        "02".equals(transactionStatus) && !"00".equals(responseCode) && !"07".equals(responseCode);
    if (success) {
      String transactionNo = params.get("vnp_TransactionNo");
      if (transactionNo == null || transactionNo.isBlank() || "0".equals(transactionNo))
        return "{\"RspCode\":\"99\",\"Message\":\"Invalid transaction\"}";
      Instant chargedAt;
      if (params.get("vnp_PayDate") == null)
        return "{\"RspCode\":\"99\",\"Message\":\"Invalid payment date\"}";
      try {
        chargedAt =
            LocalDateTime.parse(
                    params.get("vnp_PayDate"),
                    DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
                        .withResolverStyle(ResolverStyle.STRICT))
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toInstant();
      } catch (java.time.DateTimeException ex) {
        return "{\"RspCode\":\"99\",\"Message\":\"Invalid payment date\"}";
      }
      if (charges.existsByProviderAndMerchantAccountAndProviderChargeId(
          payment.getProvider(), payment.getMerchantAccount(), transactionNo))
        return "{\"RspCode\":\"02\",\"Message\":\"Order already confirmed\"}";
      charges.save(Charge.received(payment, transactionNo, chargedAt, Instant.now()));
    }
    payment.setState(
        success
            ? PaymentAttemptState.SUCCEEDED
            : failed ? PaymentAttemptState.FAILED : PaymentAttemptState.UNKNOWN);
    payment.setFinishedAt(payment.getState() == PaymentAttemptState.UNKNOWN ? null : Instant.now());
    attempts.save(payment);
    log.info("IPN processed: attemptId={}, state={}", payment.getId(), payment.getState());
    return "{\"RspCode\":\"00\",\"Message\":\"Confirm success\"}";
  }

  @Transactional(readOnly = true)
  public PaymentStart handleReturn(Map<String, String> params) {
    if (!VNPayUtil.verifySecureHash(params, vnPayConfig.getHashSecret())
        || !vnPayConfig.getTmnCode().equals(params.get("vnp_TmnCode")))
      throw new PaymentException(PaymentErrorCode.PAYMENT_INVALID_SIGNATURE);
    UUID id;
    if (params.get("vnp_TxnRef") == null)
      throw new PaymentException(PaymentErrorCode.INVALID_REQUEST);
    try {
      id = UUID.fromString(params.get("vnp_TxnRef"));
    } catch (IllegalArgumentException ex) {
      throw new PaymentException(PaymentErrorCode.INVALID_REQUEST, ex);
    }
    var payment =
        attempts
            .findById(id)
            .filter(
                p ->
                    "VNPAY".equals(p.getProvider())
                        && vnPayConfig.getTmnCode().equals(p.getMerchantAccount()))
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND));
    var snapshot =
        snapshots
            .findById(payment.getOrderId())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_READY));
    return starts.toDto(payment, snapshot.getExpiresAt());
  }

  @Transactional(readOnly = true)
  public URI buildAppReturnUri(Map<String, String> params) {
    var builder = UriComponentsBuilder.fromUriString(vnPayConfig.getAppReturnUrl());
    try {
      var payment = handleReturn(params);
      builder
          .queryParam("orderId", payment.orderId())
          .queryParam("attemptId", payment.attemptId())
          .queryParam("status", payment.attemptState().name());
      charges
          .findFirstByAttemptId(payment.attemptId())
          .ifPresent(c -> builder.queryParam("transactionId", c.getProviderChargeId()));
      if (params.get("vnp_ResponseCode") != null)
        builder.queryParam("responseCode", params.get("vnp_ResponseCode"));
    } catch (PaymentException ex) {
      builder.queryParam("status", "FAILED");
    }
    return builder.build().encode().toUri();
  }

  private String buildVNPayUrl(
      OrderPaymentSnapshot snapshot, PaymentAttempt payment, String clientIp) {
    Map<String, String> params = new HashMap<>();
    params.put("vnp_Version", vnPayConfig.getVersion());
    params.put("vnp_Command", vnPayConfig.getCommand());
    params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
    params.put(
        "vnp_Amount",
        snapshot
            .getExpectedAmount()
            .multiply(BigDecimal.valueOf(100))
            .toBigIntegerExact()
            .toString());
    params.put("vnp_CurrCode", vnPayConfig.getCurrencyCode());
    params.put("vnp_TxnRef", payment.getId().toString());
    params.put("vnp_OrderInfo", "Thanh toan ve " + snapshot.getOrderId());
    params.put("vnp_OrderType", "billpayment");
    params.put("vnp_Locale", vnPayConfig.getLocale());
    params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
    params.put("vnp_IpAddr", clientIp);
    params.put("vnp_CreateDate", VNPayUtil.getCurrentTime());
    var expires =
        snapshot.getExpiresAt().isBefore(Instant.now().plusSeconds(900))
            ? DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(snapshot.getExpiresAt())
            : VNPayUtil.getExpireTime(15);
    params.put("vnp_ExpireDate", expires);
    return VNPayUtil.buildPaymentUrl(
        vnPayConfig.getPaymentUrl(), params, vnPayConfig.getHashSecret());
  }

  private String resolveClientIp(CreatePaymentRequest request, HttpServletRequest httpRequest) {
    if (request.clientIp() != null && !request.clientIp().isBlank()) return request.clientIp();
    String forwarded = httpRequest.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
    return httpRequest.getRemoteAddr();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PaymentView getPayment(UUID orderId, UUID requestId, String buyerSubject) {
    if (buyerSubject == null || buyerSubject.isBlank())
      throw new PaymentException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
    var access =
        snapshots
            .findAccessByOrderId(orderId)
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_READY));
    if (!buyerSubject.equals(access.getBuyerSubject()))
      throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
    var request = requestId == null ? null : requestResult(orderId, requestId);
    var latest =
        attempts
            .findFirstByOrderIdOrderByStartedAtDescIdDesc(orderId)
            .map(a -> starts.toDto(a, access.getExpiresAt()))
            .orElse(null);
    return views.toDto(
        orderId,
        confirmations.findChargeIdByOrderId(orderId).orElse(null),
        latest,
        request,
        refunds.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
            .map(refundMapper::toDto)
            .toList());
  }

  private StartRequestView requestResult(UUID orderId, UUID requestId) {
    var received = inbox.findById(new InboxMessageId(START_CONSUMER, requestId));
    if (received.isEmpty()) return new StartRequestView(requestId, StartRequestState.WAITING, null);
    StartPaymentResult result;
    try {
      result = messages.fromResultJson(received.get().getResponsePayload());
    } catch (ContractValidationException ex) {
      throw unavailable(orderId, requestId, ex);
    }
    if (!orderId.equals(result.orderId()))
      throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
    if (!requestId.equals(result.requestId())) throw unavailable(orderId, requestId);
    if (result.state() == StartPaymentResultState.CREATED) {
      if (attempts.findByIdAndOrderId(result.attemptId(), orderId).isEmpty())
        throw unavailable(orderId, requestId);
      return new StartRequestView(requestId, StartRequestState.CREATED, null);
    }
    return new StartRequestView(requestId, StartRequestState.REJECTED, result.failureCode());
  }

  private PaymentException unavailable(UUID orderId, UUID requestId) {
    return unavailable(orderId, requestId, null);
  }

  private PaymentException unavailable(UUID orderId, UUID requestId, Throwable cause) {
    log.warn("Payment request state unavailable orderId={} requestId={}", orderId, requestId);
    return new PaymentException(PaymentErrorCode.PAYMENT_STATE_UNAVAILABLE, cause);
  }
}
