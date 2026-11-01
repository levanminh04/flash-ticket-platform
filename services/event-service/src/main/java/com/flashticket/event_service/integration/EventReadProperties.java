package com.flashticket.event_service.integration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("event.read-model")
public record EventReadProperties(
    @DefaultValue("false") boolean enabled,
    URI paymentBaseUrl,
    URI bookingBaseUrl,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("5s") Duration responseTimeout,
    @DefaultValue("30s") Duration cancellationMaxAge,
    @DefaultValue("5s") Duration clockSkew) {
  public EventReadProperties {
    if (paymentBaseUrl != null && paymentBaseUrl.toString().isBlank()) paymentBaseUrl = null;
    if (bookingBaseUrl != null && bookingBaseUrl.toString().isBlank()) bookingBaseUrl = null;
    if (connectTimeout.isNegative()
        || connectTimeout.isZero()
        || responseTimeout.isNegative()
        || responseTimeout.isZero()
        || cancellationMaxAge.isNegative()
        || cancellationMaxAge.isZero()
        || clockSkew.isNegative())
      throw new IllegalArgumentException("Invalid Event read-model durations");
    endpoint(paymentBaseUrl);
    endpoint(bookingBaseUrl);
  }

  static void endpoint(URI uri) {
    if (uri != null
        && (!uri.isAbsolute()
            || !java.util.Set.of("http", "https").contains(uri.getScheme())
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))))
      throw new IllegalArgumentException(
          "Expected internal HTTP origin without credentials or path");
  }
}
