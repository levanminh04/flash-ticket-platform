package com.flashticket.user_service.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.user_service.web.UserException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public final class KeycloakAdminClient implements KeycloakRoles {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Role(String id, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Token(
      @JsonProperty("access_token") String value, @JsonProperty("expires_in") long expiresIn) {
    @Override
    public String toString() {
      return "Token[redacted]";
    }
  }

  private static final ParameterizedTypeReference<List<Role>> ROLES =
      new ParameterizedTypeReference<>() {};
  private final RestClient rest;
  private final String baseUrl;
  private final String realm;
  private final String clientId;
  private final String clientSecret;
  private final Clock clock;
  private final MeterRegistry metrics;
  private String token;
  private Instant expiresAt = Instant.MIN;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "The application metrics registry must be shared to publish these client measurements.")
  public KeycloakAdminClient(
      RestClient.Builder builder,
      @Value("${user.keycloak-admin.base-url:}") String baseUrl,
      @Value("${user.keycloak-admin.realm:}") String realm,
      @Value("${user.keycloak-admin.client-id:user-management}") String clientId,
      @Value("${user.keycloak-admin.client-secret:}") String clientSecret,
      @Value("${user.keycloak-admin.timeout:5s}") Duration timeout,
      Clock clock,
      MeterRegistry metrics) {
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Admin timeout must be positive");
    this.baseUrl = baseUrl;
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clock = clock;
    this.metrics = metrics;
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public void ensureOrganizer(String subject) {
    Timer.Sample sample = Timer.start(metrics);
    String outcome = "success";
    try {
      if (baseUrl.isBlank() || realm.isBlank() || clientId.isBlank() || clientSecret.isBlank())
        throw unavailable();
      try {
        ensureRoles(subject, accessToken());
      } catch (HttpClientErrorException.Unauthorized ex) {
        invalidateToken();
        ensureRoles(subject, accessToken());
      }
    } catch (RuntimeException ex) {
      outcome = "unavailable";
      // Do not propagate provider response bodies, token requests or secret-bearing exceptions.
      throw unavailable();
    } finally {
      sample.stop(Timer.builder("user.keycloak.admin").tag("outcome", outcome).register(metrics));
    }
  }

  private void ensureRoles(String subject, String bearer) {
    Set<String> required = Set.of("BUYER", "ORGANIZER");
    List<Role> current = readRoles(subject, bearer);
    List<Role> missing = new ArrayList<>();
    List<Role> available =
        required.stream().allMatch(name -> current.stream().anyMatch(r -> name.equals(r.name())))
            ? List.of()
            : readRoles(subject, bearer, true);
    for (String name : required) {
      if (current.stream().noneMatch(r -> name.equals(r.name()))) {
        // The deployed manage-users/view-users client can list this user's available roles.
        // Reading the realm-wide role catalog requires additional privileges it does not have.
        Role role =
            available.stream()
                .filter(r -> name.equals(r.name()))
                .findFirst()
                .orElseThrow(KeycloakAdminClient::unavailable);
        if (role == null || role.id() == null || role.id().isBlank() || !name.equals(role.name()))
          throw unavailable();
        missing.add(role);
      }
    }
    if (!missing.isEmpty()) {
      rest.post()
          .uri(
              b ->
                  b.pathSegment(
                          "admin", "realms", realm, "users", subject, "role-mappings", "realm")
                      .build())
          .headers(
              h -> {
                h.setBearerAuth(bearer);
                addCorrelation(h);
              })
          .contentType(MediaType.APPLICATION_JSON)
          .body(missing)
          .retrieve()
          .toBodilessEntity();
    }
    List<Role> confirmed = readRoles(subject, bearer);
    if (!required.stream()
        .allMatch(name -> confirmed.stream().anyMatch(r -> name.equals(r.name()))))
      throw unavailable();
  }

  private List<Role> readRoles(String subject, String bearer) {
    return readRoles(subject, bearer, false);
  }

  private List<Role> readRoles(String subject, String bearer, boolean available) {
    List<Role> roles =
        rest.get()
            .uri(
                b -> {
                  b.pathSegment(
                      "admin", "realms", realm, "users", subject, "role-mappings", "realm");
                  if (available) b.pathSegment("available");
                  return b.build();
                })
            .headers(
                h -> {
                  h.setBearerAuth(bearer);
                  addCorrelation(h);
                })
            .retrieve()
            .body(ROLES);
    if (roles == null || roles.stream().anyMatch(r -> r == null || r.name() == null))
      throw unavailable();
    return roles;
  }

  private synchronized String accessToken() {
    if (token != null && clock.instant().isBefore(expiresAt)) return token;
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    Token response =
        rest.post()
            .uri(b -> b.pathSegment("realms", realm, "protocol", "openid-connect", "token").build())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Token.class);
    if (response == null
        || response.value() == null
        || response.value().isBlank()
        || response.expiresIn() <= 0) throw unavailable();
    token = response.value();
    expiresAt =
        clock.instant().plusSeconds(response.expiresIn() - Math.min(30, response.expiresIn() / 2));
    return token;
  }

  private synchronized void invalidateToken() {
    token = null;
    expiresAt = Instant.MIN;
  }

  private static UserException unavailable() {
    return new UserException(
        503, "ROLE_GRANT_UNAVAILABLE", "Organizer role could not be confirmed; retry is safe");
  }

  private static void addCorrelation(org.springframework.http.HttpHeaders headers) {
    String correlation = MDC.get("correlationId");
    if (correlation != null) headers.set("X-Correlation-Id", correlation);
  }
}
