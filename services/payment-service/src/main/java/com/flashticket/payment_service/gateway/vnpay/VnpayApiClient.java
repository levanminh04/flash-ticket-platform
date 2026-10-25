package com.flashticket.payment_service.gateway.vnpay;

import com.flashticket.payment_service.config.VnpayProperties;
import com.flashticket.payment_service.gateway.GatewayException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/** Serializes one API request and decodes its strictly authenticated JSON response. */
public final class VnpayApiClient {
  static final int MAX_BODY = 65536;

  @FunctionalInterface
  public interface Transport {
    String post(URI endpoint, String payload, Duration timeout);
  }

  private final String apiUrl;
  private final Duration readTimeout;
  private final Transport transport;
  private final VnpayApiSignature signatures;
  private final JsonMapper mapper =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  public VnpayApiClient(VnpayProperties properties) {
    this(properties, httpTransport(properties.getConnectTimeout()));
  }

  public VnpayApiClient(VnpayProperties properties, Transport transport) {
    var settings = Objects.requireNonNull(properties).snapshot();
    apiUrl = settings.getApiUrl();
    readTimeout = settings.getReadTimeout();
    signatures = new VnpayApiSignature(settings.getHashSecret(), settings.getTmnCode());
    this.transport = Objects.requireNonNull(transport);
  }

  public Map<String, String> post(String command, Map<String, String> data) {
    var request = signatures.signRequest(command, data);
    String body;
    try {
      body = transport.post(URI.create(apiUrl), mapper.writeValueAsString(request), readTimeout);
    } catch (GatewayException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new GatewayException(GatewayException.Code.UNAVAILABLE);
    }
    try {
      if (body == null
          || body.length() > MAX_BODY
          || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY) throw VnpayCodec.invalid();
      var tree = mapper.readTree(body);
      if (tree == null || !tree.isObject() || tree.size() > 64) throw VnpayCodec.invalid();
      var response = new LinkedHashMap<String, String>();
      for (var name : tree.propertyNames()) {
        var value = tree.get(name);
        if (!(value.isString() || value.isIntegralNumber() || value.isNull()))
          throw VnpayCodec.invalid();
        response.put(
            name, value.isNull() ? "" : value.isString() ? value.asString() : value.toString());
      }
      signatures.verifyResponse(command, response);
      return Map.copyOf(response);
    } catch (GatewayException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw VnpayCodec.invalid();
    }
  }

  static Transport httpTransport(Duration connectTimeout) {
    return new VnpayHttpTransport(connectTimeout);
  }
}
