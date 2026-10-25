package com.flashticket.payment_service.gateway.vnpay;

import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

final class VnpayApiFixtures {
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String[] RESPONSE_FIELDS = {
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
    "vnp_OrderInfo"
  };

  private VnpayApiFixtures() {}

  static Map<String, String> response(String command) {
    var m = VnpayTestFixtures.notification();
    m.put("vnp_ResponseId", "response123");
    m.put("vnp_Command", command);
    m.put("vnp_Message", "Success");
    m.put("vnp_BankCode", "NCB");
    m.put("vnp_TransactionType", command.equals("refund") ? "02" : "01");
    m.put("vnp_OrderInfo", "Fixture");
    return m;
  }

  static String responseBody(Map<String, String> values) {
    try {
      var parts = new ArrayList<String>();
      for (var name : RESPONSE_FIELDS) parts.add(values.getOrDefault(name, ""));
      if ("querydr".equals(values.get("vnp_Command"))) {
        parts.add(values.getOrDefault("vnp_PromotionCode", ""));
        parts.add(values.getOrDefault("vnp_PromotionAmount", ""));
      }
      var mac = javax.crypto.Mac.getInstance("HmacSHA512");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              "key".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      var data = new TreeMap<>(values);
      data.put(
          "vnp_SecureHash",
          HexFormat.of()
              .formatHex(mac.doFinal(String.join("|", parts).getBytes(StandardCharsets.UTF_8))));
      return JSON.writeValueAsString(data);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
