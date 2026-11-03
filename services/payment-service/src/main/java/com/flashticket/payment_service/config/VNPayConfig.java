// VNPayConfig.java
package com.flashticket.payment_service.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class VNPayConfig {

  @Value("${vnpay.tmn-code}")
  private String tmnCode;

  @Value("${vnpay.hash-secret}")
  private String hashSecret;

  @Value("${vnpay.payment-url}")
  private String paymentUrl;

  @Value("${vnpay.api-url}")
  private String apiUrl;

  @Value("${vnpay.server-ip:127.0.0.1}")
  private String serverIp;

  @Value("${vnpay.refund-create-by:flash-ticket}")
  private String refundCreateBy;

  @Value("${vnpay.return-url}")
  private String returnUrl;

  @Value("${vnpay.ipn-url}")
  private String ipnUrl;

  @Value("${vnpay.app-return-url:flash-ticket://payment-return}")
  private String appReturnUrl;

  @Value("${vnpay.version:2.1.0}")
  private String version;

  @Value("${vnpay.command:pay}")
  private String command;

  @Value("${vnpay.currency-code:VND}")
  private String currencyCode;

  @Value("${vnpay.locale:vn}")
  private String locale;
}
