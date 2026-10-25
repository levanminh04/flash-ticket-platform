package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayApiFixtures.*;
import static com.flashticket.payment_service.gateway.vnpay.VnpayTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class VnpayHttpTransportTest {
  @Test
  void boundsTheWholeResponseWhenHeadersArriveBeforeTheBodyStalls() throws Exception {
    var headersSent = new java.util.concurrent.atomic.AtomicBoolean();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/warmup",
        e -> {
          e.sendResponseHeaders(200, 2);
          try (var out = e.getResponseBody()) {
            out.write(new byte[] {'o', 'k'});
          }
        });
    server.createContext(
        "/body",
        e -> {
          e.sendResponseHeaders(200, 2);
          try (var out = e.getResponseBody()) {
            out.write('o');
            out.flush();
            headersSent.set(true);
            Thread.sleep(400);
            out.write('k');
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } catch (java.io.IOException ex) {
            /* Deadline cancellation can close this test connection. */
          }
        });
    server.start();
    try {
      var base = "http://127.0.0.1:" + server.getAddress().getPort();
      var transport = VnpayApiClient.httpTransport(Duration.ofSeconds(1));
      assertThat(transport.post(URI.create(base + "/warmup"), "{}", Duration.ofSeconds(2)))
          .isEqualTo("ok");
      assertThatThrownBy(
              () -> transport.post(URI.create(base + "/body"), "{}", Duration.ofMillis(100)))
          .isInstanceOf(GatewayException.class);
      assertThat(headersSent).isTrue();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void actualHttpTransportDoesNotFollowRedirectAndBoundsBodyAndWait() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/redirect",
        e -> {
          e.getResponseHeaders().add("Location", "/success");
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    server.createContext(
        "/large",
        e -> {
          var bytes = new byte[65537];
          e.sendResponseHeaders(200, bytes.length);
          try (var out = e.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.createContext(
        "/slow",
        e -> {
          try {
            Thread.sleep(500);
            e.sendResponseHeaders(200, -1);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            e.close();
          }
        });
    server.start();
    try {
      var transport = VnpayApiClient.httpTransport(Duration.ofMillis(200));
      for (var path : List.of("/redirect", "/large", "/slow"))
        assertThatThrownBy(
                () ->
                    transport.post(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path),
                        "{}",
                        Duration.ofMillis(100)))
            .isInstanceOf(GatewayException.class)
            .hasMessageNotContaining("fixture-secret");
    } finally {
      server.stop(0);
    }
  }
}
