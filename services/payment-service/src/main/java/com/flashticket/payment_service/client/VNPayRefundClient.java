package com.flashticket.payment_service.client;

import com.flashticket.payment_service.config.VNPayConfig;
import com.flashticket.payment_service.dto.response.RefundGatewayRequest;
import com.flashticket.payment_service.dto.response.RefundGatewayResult;
import com.flashticket.payment_service.entity.enums.RefundGatewayOutcome;
import com.flashticket.payment_service.util.VNPayUtil;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class VNPayRefundClient {
  private final RestClient vnpayRestClient;
  private final VNPayConfig vnPayConfig;

  public RefundGatewayResult refund(RefundGatewayRequest request) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("vnp_RequestId", request.requestKey());
    body.put("vnp_Version", vnPayConfig.getVersion());
    body.put("vnp_Command", "refund");
    body.put("vnp_TmnCode", vnPayConfig.getTmnCode());
    body.put("vnp_TransactionType", "02");
    body.put("vnp_TxnRef", request.txnRef());
    body.put(
        "vnp_Amount",
        request.amount().multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString());
    body.put("vnp_OrderInfo", "Hoan tien " + request.refundId());
    body.put("vnp_TransactionNo", request.transactionNo());
    body.put("vnp_TransactionDate", request.transactionDate());
    body.put("vnp_CreateBy", vnPayConfig.getRefundCreateBy());
    body.put("vnp_CreateDate", VNPayUtil.getCurrentTime());
    body.put("vnp_IpAddr", vnPayConfig.getServerIp());
    body.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            vnPayConfig.getHashSecret(),
            String.join(
                "|",
                body.get("vnp_RequestId"),
                body.get("vnp_Version"),
                body.get("vnp_Command"),
                body.get("vnp_TmnCode"),
                body.get("vnp_TransactionType"),
                body.get("vnp_TxnRef"),
                body.get("vnp_Amount"),
                body.get("vnp_TransactionNo"),
                body.get("vnp_TransactionDate"),
                body.get("vnp_CreateBy"),
                body.get("vnp_CreateDate"),
                body.get("vnp_IpAddr"),
                body.get("vnp_OrderInfo"))));
    try {
      Map<String, Object> raw =
          vnpayRestClient
              .post()
              .uri("")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, String> response = new LinkedHashMap<>();
      if (raw != null) {
        for (var entry : raw.entrySet()) {
          response.put(
              entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
      }
      if (!verifyResponseHash(
          response,
          List.of(
              "vnp_ResponseId",
              "vnp_Command",
              "vnp_ResponseCode",
              "vnp_Message",
              "vnp_TmnCode",
              "vnp_TxnRef",
              "vnp_Amount",
              "vnp_BankCode",
              "vnp_PayDate",
              "vnp_TransactionNo",
              "vnp_TransactionType",
              "vnp_TransactionStatus",
              "vnp_OrderInfo"))) {
        return new RefundGatewayResult(RefundGatewayOutcome.UNKNOWN, null, "INVALID_SIGNATURE");
      }
      String responseCode = response.get("vnp_ResponseCode");
      return new RefundGatewayResult(
          toOutcome(
              responseCode,
              response.get("vnp_TransactionType"),
              response.get("vnp_TransactionStatus"),
              false),
          response.get("vnp_TransactionNo"),
          responseCode);
    } catch (RestClientException ex) {
      log.warn("VNPay refund {}: {}", request.refundId(), ex.getClass().getSimpleName());
      // Timeout chưa rõ tiền đã hoàn hay chưa; phải hỏi lại bằng querydr.
      return new RefundGatewayResult(RefundGatewayOutcome.UNKNOWN, null, "GATEWAY_UNAVAILABLE");
    }
  }

  public RefundGatewayResult queryRefund(RefundGatewayRequest request) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
    body.put("vnp_Version", vnPayConfig.getVersion());
    body.put("vnp_Command", "querydr");
    body.put("vnp_TmnCode", vnPayConfig.getTmnCode());
    body.put("vnp_TxnRef", request.txnRef());
    body.put("vnp_OrderInfo", "Truy van hoan tien " + request.refundId());
    body.put("vnp_TransactionNo", request.transactionNo());
    body.put("vnp_TransactionDate", request.transactionDate());
    body.put("vnp_CreateDate", VNPayUtil.getCurrentTime());
    body.put("vnp_IpAddr", vnPayConfig.getServerIp());
    body.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            vnPayConfig.getHashSecret(),
            String.join(
                "|",
                body.get("vnp_RequestId"),
                body.get("vnp_Version"),
                body.get("vnp_Command"),
                body.get("vnp_TmnCode"),
                body.get("vnp_TxnRef"),
                body.get("vnp_TransactionDate"),
                body.get("vnp_CreateDate"),
                body.get("vnp_IpAddr"),
                body.get("vnp_OrderInfo"))));
    try {
      Map<String, Object> raw =
          vnpayRestClient
              .post()
              .uri("")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, String> response = new LinkedHashMap<>();
      if (raw != null) {
        for (var entry : raw.entrySet()) {
          response.put(
              entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
      }
      if (!verifyResponseHash(
          response,
          List.of(
              "vnp_ResponseId",
              "vnp_Command",
              "vnp_ResponseCode",
              "vnp_Message",
              "vnp_TmnCode",
              "vnp_TxnRef",
              "vnp_Amount",
              "vnp_BankCode",
              "vnp_PayDate",
              "vnp_TransactionNo",
              "vnp_TransactionType",
              "vnp_TransactionStatus",
              "vnp_OrderInfo",
              "vnp_PromotionCode",
              "vnp_PromotionAmount"))) {
        return new RefundGatewayResult(RefundGatewayOutcome.UNKNOWN, null, "INVALID_SIGNATURE");
      }
      String responseCode = response.get("vnp_ResponseCode");
      return new RefundGatewayResult(
          toOutcome(
              responseCode,
              response.get("vnp_TransactionType"),
              response.get("vnp_TransactionStatus"),
              true),
          response.get("vnp_TransactionNo"),
          responseCode);
    } catch (RestClientException ex) {
      log.warn("VNPay refund {}: {}", request.refundId(), ex.getClass().getSimpleName());
      // Timeout chưa rõ tiền đã hoàn hay chưa; phải hỏi lại bằng querydr.
      return new RefundGatewayResult(RefundGatewayOutcome.UNKNOWN, null, "GATEWAY_UNAVAILABLE");
    }
  }

  private boolean verifyResponseHash(Map<String, String> response, List<String> fieldOrder) {
    String receivedHash = response.get("vnp_SecureHash");
    if (receivedHash == null) return false;
    var values = new ArrayList<String>();
    for (String field : fieldOrder) {
      String value = response.get(field);
      values.add(value == null ? "" : value);
    }
    return VNPayUtil.hmacSHA512(vnPayConfig.getHashSecret(), String.join("|", values))
        .equalsIgnoreCase(receivedHash);
  }

  private RefundGatewayOutcome toOutcome(
      String responseCode, String transactionType, String transactionStatus, boolean query) {
    if ("00".equals(responseCode)) {
      if (query && "01".equals(transactionType)) return RefundGatewayOutcome.FAILED;
      if (query && !"02".equals(transactionType)) return RefundGatewayOutcome.UNKNOWN;
      if ("00".equals(transactionStatus)) return RefundGatewayOutcome.SUCCEEDED;
      if ("05".equals(transactionStatus) || "06".equals(transactionStatus))
        return RefundGatewayOutcome.PROCESSING;
      if ("09".equals(transactionStatus)) return RefundGatewayOutcome.FAILED;
      return RefundGatewayOutcome.UNKNOWN;
    }
    if ("91".equals(responseCode)) return RefundGatewayOutcome.FAILED;
    if (!query) {
      if ("94".equals(responseCode)) return RefundGatewayOutcome.PROCESSING;
      if ("02".equals(responseCode)
          || "03".equals(responseCode)
          || "95".equals(responseCode)
          || "97".equals(responseCode)) return RefundGatewayOutcome.FAILED;
    }
    // Mã 99 chưa rõ tiền đã hoàn hay chưa; phải hỏi lại bằng querydr.
    return RefundGatewayOutcome.UNKNOWN;
  }
}
