// VNPayUtil.java
package com.flashticket.payment_service.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class VNPayUtil {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  /** HMAC-SHA512 để tạo và verify secure hash */
  public static String hmacSHA512(String key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException("Cannot compute HMAC-SHA512", e);
    }
  }

  /**
   * Build query string đã encode + tính vnp_SecureHash. Params phải được sort theo key alphabet
   * trước khi hash.
   */
  public static String buildPaymentUrl(
      String baseUrl, Map<String, String> params, String hashSecret) {
    // Sort theo key
    List<String> fieldNames = new ArrayList<>(params.keySet());
    Collections.sort(fieldNames);

    StringBuilder hashData = new StringBuilder();
    StringBuilder query = new StringBuilder();

    for (String field : fieldNames) {
      String value = params.get(field);
      if (value != null && !value.isEmpty()) {
        hashData
            .append(field)
            .append('=')
            .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        query
            .append(URLEncoder.encode(field, StandardCharsets.US_ASCII))
            .append('=')
            .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));

        if (fieldNames.indexOf(field) < fieldNames.size() - 1) {
          hashData.append('&');
          query.append('&');
        }
      }
    }

    String secureHash = hmacSHA512(hashSecret, hashData.toString());
    return baseUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
  }

  /** Verify callback/IPN từ VNPay — tách secureHash ra, hash phần còn lại */
  public static boolean verifySecureHash(Map<String, String> params, String hashSecret) {
    String receivedHash = params.get("vnp_SecureHash");
    if (receivedHash == null) return false;

    Map<String, String> copy = new HashMap<>(params);
    copy.remove("vnp_SecureHash");
    copy.remove("vnp_SecureHashType");

    List<String> fieldNames = new ArrayList<>(copy.keySet());
    Collections.sort(fieldNames);

    StringBuilder hashData = new StringBuilder();
    for (int i = 0; i < fieldNames.size(); i++) {
      String field = fieldNames.get(i);
      String value = copy.get(field);
      if (value != null && !value.isEmpty()) {
        hashData
            .append(field)
            .append('=')
            .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        if (i < fieldNames.size() - 1) hashData.append('&');
      }
    }

    String computedHash = hmacSHA512(hashSecret, hashData.toString());
    return computedHash.equalsIgnoreCase(receivedHash);
  }

  public static String getCurrentTime() {
    return LocalDateTime.now(VNPAY_ZONE).format(FORMATTER);
  }

  public static String getExpireTime(int minutes) {
    return LocalDateTime.now(VNPAY_ZONE).plusMinutes(minutes).format(FORMATTER);
  }
}
