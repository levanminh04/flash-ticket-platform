package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayApiClient.MAX_BODY;

import com.flashticket.payment_service.gateway.GatewayException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** One bounded HTTP exchange, including response body deadline and cancellation. */
final class VnpayHttpTransport implements VnpayApiClient.Transport {
  private final HttpClient client;

  VnpayHttpTransport(Duration connectTimeout) {
    client =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public String post(URI endpoint, String payload, Duration timeout) {

    if (payload.length() > MAX_BODY) throw VnpayCodec.invalid();
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
    var buffer = new ByteArrayOutputStream();
    var pending =
        client.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofByteArrayConsumer(
                chunk ->
                    chunk.ifPresent(
                        bytes -> {
                          if (buffer.size() + bytes.length > MAX_BODY) throw VnpayCodec.invalid();
                          buffer.writeBytes(bytes);
                        })));
    try {
      // On JDK 21 HttpRequest.timeout only covers receipt of headers. Bound body completion too.
      var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
      if (response.statusCode() != 200)
        throw new GatewayException(GatewayException.Code.UNAVAILABLE);
      return buffer.toString(StandardCharsets.UTF_8);
    } catch (InterruptedException ex) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw new GatewayException(GatewayException.Code.UNAVAILABLE);
    } catch (TimeoutException | ExecutionException | RuntimeException ex) {
      pending.cancel(true);
      throw new GatewayException(GatewayException.Code.UNAVAILABLE);
    }
  }
}
