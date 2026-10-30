package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashticket.user_service.identity.KeycloakAdminClient;
import com.flashticket.user_service.web.UserException;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class KeycloakAdminClientTests {
  private HttpServer server;
  private final Set<String> roles = new HashSet<>();
  private final AtomicInteger tokens = new AtomicInteger();
  private final AtomicInteger writes = new AtomicInteger();
  private boolean ignoreGrants;
  private boolean unauthorizedOnce;
  private volatile boolean slowRoles;
  private boolean denyRealmCatalog;

  @BeforeEach
  void setup() throws Exception {
    roles.add("BUYER");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String body;
          int status = 200;
          if (path.endsWith("/token")) {
            tokens.incrementAndGet();
            body = "{\"access_token\":\"test-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}";
          } else if (path.endsWith("/role-mappings/realm")) {
            if (slowRoles) {
              try {
                Thread.sleep(800);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
            }
            if (unauthorizedOnce) {
              unauthorizedOnce = false;
              status = 401;
              body = "{}";
            } else if (exchange.getRequestMethod().equals("POST")) {
              writes.incrementAndGet();
              var input = JsonMapper.builder().build().readTree(exchange.getRequestBody());
              if (!ignoreGrants) input.forEach(n -> roles.add(n.get("name").asString()));
              status = 204;
              body = "";
            } else {
              if (!exchange.getRequestMethod().equals("GET"))
                throw new AssertionError("Role changes must be additive");
              body =
                  JsonMapper.builder()
                      .build()
                      .writeValueAsString(
                          roles.stream()
                              .map(n -> new KeycloakAdminClient.Role(n + "-id", n))
                              .toList());
            }
          } else if (path.endsWith("/role-mappings/realm/available")) {
            body =
                JsonMapper.builder()
                    .build()
                    .writeValueAsString(
                        java.util.List.of("BUYER", "ORGANIZER").stream()
                            .filter(n -> !roles.contains(n))
                            .map(n -> new KeycloakAdminClient.Role(n + "-id", n))
                            .toList());
          } else if (path.contains("/roles/") && denyRealmCatalog) {
            status = 403;
            body = "{}";
          } else if (path.contains("/roles/")) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            body = "{\"id\":\"" + name + "-id\",\"name\":\"" + name + "\",\"composite\":false}";
          } else {
            status = 404;
            body = "{}";
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          try {
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) exchange.getResponseBody().write(bytes);
          } catch (java.io.IOException disconnected) {
            // Expected for the read-timeout test.
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private KeycloakAdminClient client(Duration timeout) {
    return new KeycloakAdminClient(
        RestClient.builder(),
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "test",
        "user-management",
        "test-secret-sentinel",
        timeout,
        Clock.systemUTC(),
        new SimpleMeterRegistry());
  }

  @Test
  void additiveGrantPreservesBuyerAndCachesClientCredentialsToken() {
    var client = client(Duration.ofSeconds(3));
    client.ensureOrganizer("buyer-subject");
    client.ensureOrganizer("buyer-subject");
    assertThat(roles).containsExactlyInAnyOrder("BUYER", "ORGANIZER");
    assertThat(tokens.get()).isEqualTo(1);
    assertThat(writes.get()).isEqualTo(1);
  }

  @Test
  void grantsUsingExistingUserManagementPermissionsWithoutRealmCatalogAccess() {
    denyRealmCatalog = true;
    client(Duration.ofSeconds(3)).ensureOrganizer("buyer-subject");
    assertThat(roles).containsExactlyInAnyOrder("BUYER", "ORGANIZER");
    assertThat(writes).hasValue(1);
  }

  @Test
  void successfulWriteWithoutConfirmedRoleIsNotSuccess() {
    ignoreGrants = true;
    assertThatThrownBy(() -> client(Duration.ofSeconds(3)).ensureOrganizer("buyer-subject"))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> {
              assertThat(e.status()).isEqualTo(503);
              assertThat(e.code()).isEqualTo("ROLE_GRANT_UNAVAILABLE");
            });
    assertThat(roles).containsExactly("BUYER");
  }

  @Test
  void unauthorizedTokenIsRenewedOnce() {
    unauthorizedOnce = true;
    client(Duration.ofSeconds(3)).ensureOrganizer("buyer-subject");
    assertThat(tokens.get()).isEqualTo(2);
    assertThat(roles).contains("BUYER", "ORGANIZER");
  }

  @Test
  void readTimeoutReturnsSafeRetryableError() {
    slowRoles = true;
    assertThatThrownBy(() -> client(Duration.ofMillis(250)).ensureOrganizer("buyer-subject"))
        .isInstanceOfSatisfying(UserException.class, e -> assertThat(e.status()).isEqualTo(503))
        .hasMessageNotContaining("test-secret-sentinel");
  }
}
