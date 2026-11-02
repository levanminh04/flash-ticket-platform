package com.flashticket.payment_service.util;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VNPayUtilTest {
  @Test
  void matchesIndependentRfc4231HmacVector() {
    assertThat(VNPayUtil.hmacSHA512("Jefe", "what do ya want for nothing?"))
        .isEqualTo(
            "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737");
  }

  @Test
  void signsEncodedValuesUsedByTheCinemaPaymentService() {
    var params = Map.of("vnp_OrderInfo", "Thanh toan A & B", "vnp_Amount", "100000");
    var url = VNPayUtil.buildPaymentUrl("https://example.test/pay", params, "Jefe");
    assertThat(url)
        .startsWith(
            "https://example.test/pay?vnp_Amount=100000&vnp_OrderInfo=Thanh+toan+A+%26+B&vnp_SecureHash=");
    assertThat(url)
        .endsWith(
            "vnp_SecureHash=7ecb076c626c124818260c625ec7290c3219b7d49a2000bc257d9d39d75580a16ead3e25debf3ba28e595d0568298a29ada8b552e2a30b0a5f49849a353066b5");
    var decoded = new HashMap<String, String>();
    for (String part : URI.create(url).getRawQuery().split("&")) {
      var pair = part.split("=", 2);
      decoded.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.US_ASCII));
    }
    assertThat(VNPayUtil.verifySecureHash(decoded, "Jefe")).isTrue();
    decoded.put("vnp_Amount", "200000");
    assertThat(VNPayUtil.verifySecureHash(decoded, "Jefe")).isFalse();
  }

  @Test
  void verifiesTheSignatureWithoutMutatingCallbackParameters() {
    var params = new HashMap<String, String>();
    params.put("vnp_OrderInfo", "what do ya want for nothing?");
    params.put("vnp_SecureHash", "invalid");
    params.put("vnp_SecureHashType", "HMACSHA512");
    var original = Map.copyOf(params);
    assertThat(VNPayUtil.verifySecureHash(params, "Jefe")).isFalse();
    assertThat(params).isEqualTo(original);
    assertThat(VNPayUtil.verifySecureHash(Map.of("vnp_Amount", "100"), "Jefe")).isFalse();
  }
}
