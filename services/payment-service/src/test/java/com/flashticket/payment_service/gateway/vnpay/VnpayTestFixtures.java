package com.flashticket.payment_service.gateway.vnpay;

import com.flashticket.payment_service.config.VnpayProperties;
import com.flashticket.payment_service.gateway.PaymentContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

final class VnpayTestFixtures {
  static final UUID ATTEMPT = UUID.fromString("11111111-2222-4333-8444-555555555555");
  static final Instant START = Instant.parse("2026-10-25T03:00:00Z");
  static final Clock CLOCK = Clock.fixed(START.plusSeconds(10), ZoneOffset.UTC);

  private VnpayTestFixtures() {}

  static VnpayProperties properties() {
    var p = new VnpayProperties();
    p.setEnabled(true);
    p.setTmnCode("TEST1234");
    p.setHashSecret("key");
    p.setReturnUrl("https://merchant.example.test/api/v1/payments/vnpay/return");
    p.setIpnUrl("https://merchant.example.test/api/v1/payments/vnpay/ipn");
    p.setSandboxPayerIp("127.0.0.1");
    p.setApiClientIp("127.0.0.1");
    p.validate(new String[0]);
    return p;
  }

  static PaymentContext context() {
    return new PaymentContext(
        ATTEMPT,
        UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
        "TEST1234",
        new BigDecimal("100000"),
        "VND",
        START,
        START.plusSeconds(600));
  }

  static String golden(String name) {
    return tools.jackson.databind.json.JsonMapper.builder()
        .build()
        .readTree(
            Objects.requireNonNull(
                VnpayTestFixtures.class.getResourceAsStream("/vnpay/golden-vectors.json")))
        .get(name)
        .asString();
  }

  static Map<String, String> notification() {
    var m = new TreeMap<String, String>();
    m.put("vnp_TmnCode", "TEST1234");
    m.put("vnp_TxnRef", "11111111222243338444555555555555");
    m.put("vnp_Amount", "10000000");
    m.put("vnp_ResponseCode", "00");
    m.put("vnp_TransactionStatus", "00");
    m.put("vnp_TransactionNo", "123456");
    m.put("vnp_PayDate", "20261025100100");
    return m;
  }

  static String signed(Map<String, String> m) {
    try {
      var query = new StringJoiner("&");
      new TreeMap<>(m)
          .forEach(
              (k, v) ->
                  query.add(
                      java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8)
                          + "="
                          + java.net.URLEncoder.encode(
                              v, java.nio.charset.StandardCharsets.UTF_8)));
      var mac = javax.crypto.Mac.getInstance("HmacSHA512");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              "key".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
      return query
          + "&vnp_SecureHash="
          + HexFormat.of()
              .formatHex(
                  mac.doFinal(query.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
