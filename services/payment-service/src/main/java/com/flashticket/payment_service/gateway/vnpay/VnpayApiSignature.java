package com.flashticket.payment_service.gateway.vnpay;

import java.util.*;

/** API canonical field order and authentication; independent of HTTP and JSON. */
final class VnpayApiSignature {
  private static final List<String> QUERY_REQUEST =
      List.of(
          "vnp_RequestId",
          "vnp_Version",
          "vnp_Command",
          "vnp_TmnCode",
          "vnp_TxnRef",
          "vnp_TransactionDate",
          "vnp_CreateDate",
          "vnp_IpAddr",
          "vnp_OrderInfo");
  private static final List<String> REFUND_REQUEST =
      List.of(
          "vnp_RequestId",
          "vnp_Version",
          "vnp_Command",
          "vnp_TmnCode",
          "vnp_TransactionType",
          "vnp_TxnRef",
          "vnp_Amount",
          "vnp_TransactionNo",
          "vnp_TransactionDate",
          "vnp_CreateBy",
          "vnp_CreateDate",
          "vnp_IpAddr",
          "vnp_OrderInfo");
  private static final List<String> REFUND_RESPONSE =
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
          "vnp_OrderInfo");
  private static final List<String> QUERY_RESPONSE;

  static {
    var fields = new ArrayList<>(REFUND_RESPONSE);
    fields.add("vnp_PromotionCode");
    fields.add("vnp_PromotionAmount");
    QUERY_RESPONSE = List.copyOf(fields);
  }

  private final String secret;
  private final String merchantAccount;

  VnpayApiSignature(String secret, String merchantAccount) {
    this.secret = secret;
    this.merchantAccount = merchantAccount;
  }

  Map<String, String> signRequest(String command, Map<String, String> data) {
    if (!Set.of("querydr", "refund").contains(command)) throw VnpayCodec.invalid();
    var request = new LinkedHashMap<>(data);
    var requestFields = command.equals("querydr") ? QUERY_REQUEST : REFUND_REQUEST;
    request.put(
        "vnp_SecureHash", VnpayCodec.hmac(secret, VnpayCodec.fields(request, requestFields)));
    return request;
  }

  void verifyResponse(String command, Map<String, String> response) {
    VnpayCodec.verify(
        secret,
        VnpayCodec.fields(response, command.equals("querydr") ? QUERY_RESPONSE : REFUND_RESPONSE),
        response.get("vnp_SecureHash"));
    if (!command.equals(response.get("vnp_Command"))
        || !merchantAccount.equals(response.get("vnp_TmnCode"))
        || !response.getOrDefault("vnp_ResponseId", "").matches("[A-Za-z0-9]{1,32}"))
      throw VnpayCodec.invalid();
  }
}
