package com.flashticket.event_service.integration;

import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.observability.EventContext;
import com.flashticket.event_service.web.EventProblem;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HttpEventSummaries implements EventSummaries {
  private final EventReadProperties properties;
  private final DiscoveryClient discovery;
  private final InternalAccessToken tokens;
  private final JsonMapper json;
  private final RestClient http;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed beans; no dependency accessor is exposed.")
  public HttpEventSummaries(
      EventReadProperties properties,
      DiscoveryClient discovery,
      InternalAccessToken tokens,
      JsonMapper json) {
    this.properties = properties;
    this.discovery = discovery;
    this.tokens = tokens;
    this.json = json;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(properties.responseTimeout());
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public Financial financial(UUID event, String organizer, boolean requireFresh) {
    var result =
        read(
            "payment-service",
            properties.paymentBaseUrl(),
            event,
            "financial-summary",
            Financial.class);
    try {
      scope(
          result.schemaVersion(),
          result.eventId(),
          result.organizerSubject(),
          result.asOf(),
          event,
          organizer);
      if (result.hasCollectedOrders() == null || !"VND".equals(result.currency())) throw invalid();
      var gross = money(result.validGross());
      var refunds = money(result.validRefunds());
      var fee = money(result.feeAmount());
      var net = money(result.netAmount());
      if (refunds.compareTo(gross) > 0 || gross.subtract(refunds).subtract(fee).compareTo(net) != 0)
        throw invalid();
      if (!result.hasCollectedOrders() && gross.signum() > 0) throw invalid();
      if (requireFresh
          && ContractRules.instant(result.asOf(), "asOf")
              .isBefore(Instant.now().minus(properties.cancellationMaxAge())))
        throw new EventProblem(503, "UPSTREAM_DATA_STALE", null);
      return result;
    } catch (EventProblem error) {
      if (error.status() == 503) throw error;
      throw invalid();
    }
  }

  @Override
  public Sales sales(UUID event, String organizer) {
    var result =
        read("booking-service", properties.bookingBaseUrl(), event, "sales-summary", Sales.class);
    try {
      scope(
          result.schemaVersion(),
          result.eventId(),
          result.organizerSubject(),
          result.asOf(),
          event,
          organizer);
      ContractRules.unsigned(result.purchasedTicketCount(), "purchasedTicketCount", true);
      return result;
    } catch (EventProblem error) {
      if (error.status() == 503) throw error;
      throw invalid();
    }
  }

  private <T> T read(String service, URI configured, UUID event, String resource, Class<T> type) {
    if (!properties.enabled()) throw new EventProblem(503, "DEPENDENCY_CONTRACT_NOT_READY", null);
    URI origin = origin(service, configured);
    try {
      EventReadProperties.endpoint(origin);
      URI endpoint = origin.resolve("/internal/v1/events/" + event + "/" + resource);
      var request =
          http.get()
              .uri(endpoint)
              .headers(
                  h -> {
                    h.setBearerAuth(tokens.get());
                    h.set("X-Correlation-Id", EventContext.correlation(event).toString());
                    String trace = EventContext.traceparent();
                    if (trace != null) h.set("traceparent", trace);
                  });
      String body =
          request
              .retrieve()
              .onStatus(
                  status -> !status.is2xxSuccessful(),
                  (requestInfo, response) -> {
                    throw new EventProblem(503, "UPSTREAM_UNAVAILABLE", null);
                  })
              .body(String.class);
      if (body == null) throw invalid();
      T response = json.readValue(body, type);
      if (response == null) throw invalid();
      return response;
    } catch (org.springframework.web.client.RestClientException error) {
      throw new EventProblem(503, "UPSTREAM_UNAVAILABLE", null);
    } catch (tools.jackson.core.JacksonException | IllegalArgumentException error) {
      throw invalid();
    }
  }

  private URI origin(String service, URI configured) {
    if (configured != null) return configured;
    try {
      var instances = discovery.getInstances(service);
      if (instances.isEmpty()) throw new EventProblem(503, "UPSTREAM_UNAVAILABLE", null);
      return instances.getFirst().getUri();
    } catch (RuntimeException error) {
      // Discovery is an external dependency; never leak its implementation error or cause.
      throw new EventProblem(503, "UPSTREAM_UNAVAILABLE", null);
    }
  }

  private void scope(
      Integer schema, UUID actualEvent, String actualOwner, String asOf, UUID event, String owner) {
    if (!Integer.valueOf(1).equals(schema)
        || !event.equals(actualEvent)
        || !owner.equals(actualOwner)) throw new EventProblem(503, "UPSTREAM_SCOPE_MISMATCH", null);
    if (ContractRules.instant(asOf, "asOf").isAfter(Instant.now().plus(properties.clockSkew())))
      throw invalid();
  }

  private static BigDecimal money(String value) {
    if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
    return new BigDecimal(value);
  }

  private static EventProblem invalid() {
    return new EventProblem(503, "UPSTREAM_INVALID_RESPONSE", null);
  }
}
