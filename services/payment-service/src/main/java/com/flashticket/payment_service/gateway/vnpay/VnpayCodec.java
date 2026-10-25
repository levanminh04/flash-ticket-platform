package com.flashticket.payment_service.gateway.vnpay;

import com.flashticket.payment_service.gateway.GatewayException;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class VnpayCodec {
  private VnpayCodec() {}

  static String payQuery(Map<String, String> params) {
    var query = new StringJoiner("&");
    new TreeMap<>(params)
        .forEach(
            (key, value) -> {
              if (key.startsWith("vnp_")
                  && !key.equals("vnp_SecureHash")
                  && !key.equals("vnp_SecureHashType")
                  && value != null
                  && !value.isEmpty())
                query.add(
                    URLEncoder.encode(key, StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8));
            });
    return query.toString();
  }

  static String hmac(String secret, String data) {
    try {
      var mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new GatewayException(GatewayException.Code.UNAVAILABLE);
    }
  }

  static void verify(String secret, String data, String signature) {
    if (signature == null
        || !signature.matches("[0-9a-fA-F]{128}")
        || !MessageDigest.isEqual(
            HexFormat.of().parseHex(hmac(secret, data)), HexFormat.of().parseHex(signature)))
      throw new GatewayException(GatewayException.Code.INVALID_SIGNATURE);
  }

  static Map<String, String> rawQuery(String raw) {
    if (raw == null
        || raw.isEmpty()
        || raw.length() > 8192
        || raw.getBytes(StandardCharsets.UTF_8).length > 8192) throw invalid();
    var parts = raw.split("&", -1);
    if (parts.length > 64) throw invalid();
    var result = new LinkedHashMap<String, String>();
    for (var part : parts) {
      int separator = part.indexOf('=');
      if (separator <= 0) throw invalid();
      var key = decode(part.substring(0, separator));
      var value = decode(part.substring(separator + 1));
      if (!key.matches("[A-Za-z0-9_]+") || result.putIfAbsent(key, value) != null) throw invalid();
    }
    return Map.copyOf(result);
  }

  private static String decode(String raw) {
    var bytes = new ByteArrayOutputStream();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '%') {
        if (i + 2 >= raw.length()) throw invalid();
        int high = Character.digit(raw.charAt(++i), 16), low = Character.digit(raw.charAt(++i), 16);
        if (high < 0 || low < 0) throw invalid();
        bytes.write((high << 4) | low);
      } else {
        if (c < 33 || c > 126) throw invalid();
        bytes.write(c == '+' ? ' ' : c);
      }
    }
    try {
      var value =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes.toByteArray()))
              .toString();
      if (value.chars().anyMatch(c -> c < 32 || c == 127)) throw invalid();
      return value;
    } catch (CharacterCodingException ex) {
      throw invalid();
    }
  }

  static String fields(Map<String, String> data, List<String> names) {
    return String.join("|", names.stream().map(name -> data.getOrDefault(name, "")).toList());
  }

  static GatewayException invalid() {
    return new GatewayException(GatewayException.Code.INVALID_REQUEST);
  }
}
