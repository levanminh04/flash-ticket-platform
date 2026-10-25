package com.flashticket.payment_service.config;

import java.net.*;
import java.time.Duration;
import java.util.Arrays;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Merchant secrets must never be included in generated toString/validation messages. */
@Getter
@Setter
@ConfigurationProperties("flashticket.payment.vnpay")
public final class VnpayProperties {
  private boolean enabled;
  private boolean callbacksEnabled;
  private boolean refundEnabled;
  private String environment = "sandbox";
  private String tmnCode = "";
  private String hashSecret = "";
  private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
  private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
  private String returnUrl = "";
  private String ipnUrl = "";
  private String sandboxPayerIp = "";
  private String apiClientIp = "";
  private Duration connectTimeout = Duration.ofSeconds(2);
  private Duration readTimeout = Duration.ofSeconds(5);

  public void validate(String[] profiles) {
    require(!callbacksEnabled || enabled, "callbacks-enabled requires enabled");
    require(!refundEnabled || enabled, "refund-enabled requires enabled");
    if (!enabled) return;
    require(
        "sandbox".equals(environment)
            && Arrays.stream(profiles)
                .noneMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)),
        "sandbox only");
    require(tmnCode != null && tmnCode.matches("[A-Za-z0-9]{8}"), "tmn-code");
    require(hashSecret != null && !hashSecret.isBlank(), "hash-secret");
    endpoint(paymentUrl, "/paymentv2/vpcpay.html", "payment-url");
    endpoint(apiUrl, "/merchant_webapi/api/transaction", "api-url");
    var returns = callback(returnUrl, "/api/v1/payments/vnpay/return", "return-url");
    var ipn = callback(ipnUrl, "/api/v1/payments/vnpay/ipn", "ipn-url");
    require(
        returns.getHost().equalsIgnoreCase(ipn.getHost()) && port(returns) == port(ipn),
        "callback origin");
    require(isIp(sandboxPayerIp), "sandbox-payer-ip");
    require(isIp(apiClientIp), "api-client-ip");
    require(validTimeout(connectTimeout) && validTimeout(readTimeout), "timeout");
  }

  /** A private configuration copy for a gateway/client lifetime; never expose it to callers. */
  public VnpayProperties snapshot() {
    var copy = new VnpayProperties();
    copy.enabled = enabled;
    copy.callbacksEnabled = callbacksEnabled;
    copy.refundEnabled = refundEnabled;
    copy.environment = environment;
    copy.tmnCode = tmnCode;
    copy.hashSecret = hashSecret;
    copy.paymentUrl = paymentUrl;
    copy.apiUrl = apiUrl;
    copy.returnUrl = returnUrl;
    copy.ipnUrl = ipnUrl;
    copy.sandboxPayerIp = sandboxPayerIp;
    copy.apiClientIp = apiClientIp;
    copy.connectTimeout = connectTimeout;
    copy.readTimeout = readTimeout;
    return copy;
  }

  private static boolean validTimeout(Duration value) {
    return value != null
        && value.compareTo(Duration.ZERO) > 0
        && value.compareTo(Duration.ofSeconds(30)) <= 0;
  }

  private static void endpoint(String value, String path, String name) {
    var uri = callback(value, path, name);
    require("sandbox.vnpayment.vn".equals(uri.getHost()) && port(uri) == 443, name);
  }

  private static URI callback(String value, String path, String name) {
    require(value != null && !value.isBlank(), name);
    try {
      var uri = URI.create(value);
      require(
          "https".equals(uri.getScheme())
              && uri.getHost() != null
              && uri.getRawUserInfo() == null
              && uri.getRawQuery() == null
              && uri.getRawFragment() == null
              && path.equals(uri.getRawPath()),
          name);
      return uri;
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("Invalid VNPay configuration: " + name);
    }
  }

  private static int port(URI uri) {
    return uri.getPort() == -1 ? 443 : uri.getPort();
  }

  private static boolean isIp(String value) {
    if (value == null) return false;
    if (value.matches("[0-9]+(?:\\.[0-9]+){3}")) {
      return Arrays.stream(value.split("\\."))
          .allMatch(p -> p.length() <= 3 && Integer.parseInt(p) <= 255);
    }
    if (!value.contains(":") || !value.matches("[0-9A-Fa-f:.]+")) return false;
    try {
      return InetAddress.getByName(value) instanceof Inet6Address;
    } catch (UnknownHostException ex) {
      return false;
    }
  }

  private static void require(boolean condition, String name) {
    if (!condition) throw new IllegalStateException("Invalid VNPay configuration: " + name);
  }
}
