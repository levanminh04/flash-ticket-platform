package com.flashticket.payment_service.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.flashticket.payment_service.config.PaymentRuntimeConfig;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

@RestClientTest(BookingPurchaseClient.class)
@Import(PaymentRuntimeConfig.class)
class BookingPurchaseClientTest {
  @MockitoBean OAuth2AuthorizedClientManager authorizedClientManager;
  @MockitoBean PlatformTransactionManager transactionManager;
  @MockitoBean ContractCodec codecBean;
  @MockitoBean LoadBalancerClient loadBalancer;
  BookingPurchaseClient client;
  MockRestServiceServer server;
  PaymentContractFixtures fixtures;
  String snapshotJson;
  UUID orderId;

  @BeforeEach
  void setUp() {
    fixtures = new PaymentContractFixtures();
    try {
      snapshotJson = PaymentContractFixtures.resource("purchase.json");
    } catch (Exception ex) {
      fixtures.close();
      throw new IllegalStateException(ex);
    }
    orderId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    var registration =
        ClientRegistration.withRegistrationId("booking")
            .clientId("payment-test")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("https://issuer.example.test/token")
            .build();
    when(authorizedClientManager.authorize(any()))
        .thenReturn(
            new OAuth2AuthorizedClient(
                registration,
                "payment-service",
                new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "service-token",
                    Instant.now(),
                    Instant.now().plusSeconds(300))));
    var builder = RestClient.builder().baseUrl("https://booking.example.test");
    var interceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
    interceptor.setPrincipalResolver(new RequestAttributePrincipalResolver());
    builder.requestInterceptor(interceptor);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new BookingPurchaseClient(builder.build(), fixtures.codec);
    ReflectionTestUtils.setField(
        client, "snapshotPath", "/internal/v1/orders/{orderId}/purchase-snapshot");
  }

  @Test
  void postsSnapshotRequestWithServiceIdentity() {
    try {
      server
          .expect(
              requestTo(
                  "https://booking.example.test/internal/v1/orders/"
                      + orderId
                      + "/purchase-snapshot"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(header("Authorization", "Bearer service-token"))
          .andExpect(content().json("{\"buyerSubject\":\"buyer-1\"}"))
          .andRespond(withSuccess(snapshotJson, MediaType.APPLICATION_JSON));
      var snapshot = client.preparePurchaseSnapshot(orderId, "buyer-1");
      assertThat(snapshot.orderId()).isEqualTo(orderId);
      assertThat(snapshot.total()).isEqualTo("9999999999999999999");
      server.verify();
    } finally {
      fixtures.close();
    }
  }

  @Test
  void discoveryModeResolvesBookingAndPreservesServiceAuthentication() {
    try {
      var config = new PaymentRuntimeConfig();
      ReflectionTestUtils.setField(config, "bookingDiscoveryEnabled", true);
      ReflectionTestUtils.setField(config, "connectTimeout", Duration.ofSeconds(2));
      ReflectionTestUtils.setField(config, "readTimeout", Duration.ofSeconds(2));
      var instance =
          new DefaultServiceInstance("booking-test", "booking-service", "127.0.0.1", 18082, false);
      when(loadBalancer.reconstructURI(eq(instance), any(URI.class)))
          .thenAnswer(
              invocation ->
                  URI.create(
                      "http://127.0.0.1:18082" + invocation.<URI>getArgument(1).getRawPath()));
      when(loadBalancer.execute(
              eq("booking-service"),
              org.mockito.ArgumentMatchers.<LoadBalancerRequest<ClientHttpResponse>>any()))
          .thenAnswer(
              invocation ->
                  invocation
                      .<LoadBalancerRequest<ClientHttpResponse>>getArgument(1)
                      .apply(instance));
      var builder =
          config
              .bookingRestClient(RestClient.builder(), authorizedClientManager, loadBalancer)
              .mutate();
      server = MockRestServiceServer.bindTo(builder).build();
      client = new BookingPurchaseClient(builder.build(), fixtures.codec);
      ReflectionTestUtils.setField(
          client, "snapshotPath", "/internal/v1/orders/{orderId}/purchase-snapshot");
      server
          .expect(
              requestTo(
                  "http://127.0.0.1:18082/internal/v1/orders/" + orderId + "/purchase-snapshot"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(header("Authorization", "Bearer service-token"))
          .andExpect(content().json("{\"buyerSubject\":\"buyer-1\"}"))
          .andRespond(withSuccess(snapshotJson, MediaType.APPLICATION_JSON));
      assertThat(client.preparePurchaseSnapshot(orderId, "buyer-1").orderId()).isEqualTo(orderId);
      server.verify();
      verify(loadBalancer)
          .execute(
              eq("booking-service"),
              org.mockito.ArgumentMatchers.<LoadBalancerRequest<ClientHttpResponse>>any());
    } catch (java.io.IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    } finally {
      fixtures.close();
    }
  }

  @Test
  void directModeUsesConfiguredUrlWithoutDiscovery() {
    try {
      var config = new PaymentRuntimeConfig();
      ReflectionTestUtils.setField(config, "bookingBaseUrl", "https://booking.example.test");
      ReflectionTestUtils.setField(config, "connectTimeout", Duration.ofSeconds(2));
      ReflectionTestUtils.setField(config, "readTimeout", Duration.ofSeconds(2));
      var builder =
          config
              .bookingRestClient(RestClient.builder(), authorizedClientManager, loadBalancer)
              .mutate();
      server = MockRestServiceServer.bindTo(builder).build();
      client = new BookingPurchaseClient(builder.build(), fixtures.codec);
      ReflectionTestUtils.setField(
          client, "snapshotPath", "/internal/v1/orders/{orderId}/purchase-snapshot");
      server
          .expect(
              requestTo(
                  "https://booking.example.test/internal/v1/orders/"
                      + orderId
                      + "/purchase-snapshot"))
          .andExpect(header("Authorization", "Bearer service-token"))
          .andRespond(withSuccess(snapshotJson, MediaType.APPLICATION_JSON));
      assertThat(client.preparePurchaseSnapshot(orderId, "buyer-1").orderId()).isEqualTo(orderId);
      server.verify();
      verifyNoInteractions(loadBalancer);
    } finally {
      fixtures.close();
    }
  }

  @Test
  void missingBookingInstanceReturnsUnavailableWithoutRetry() {
    try {
      var config = new PaymentRuntimeConfig();
      ReflectionTestUtils.setField(config, "bookingDiscoveryEnabled", true);
      ReflectionTestUtils.setField(config, "connectTimeout", Duration.ofSeconds(2));
      ReflectionTestUtils.setField(config, "readTimeout", Duration.ofSeconds(2));
      when(loadBalancer.execute(
              eq("booking-service"),
              org.mockito.ArgumentMatchers.<LoadBalancerRequest<ClientHttpResponse>>any()))
          .thenThrow(new IllegalStateException("No instances available for booking-service"));
      var builder =
          config
              .bookingRestClient(RestClient.builder(), authorizedClientManager, loadBalancer)
              .mutate();
      server = MockRestServiceServer.bindTo(builder).build();
      client = new BookingPurchaseClient(builder.build(), fixtures.codec);
      ReflectionTestUtils.setField(
          client, "snapshotPath", "/internal/v1/orders/{orderId}/purchase-snapshot");
      assertThatThrownBy(() -> client.preparePurchaseSnapshot(orderId, "buyer-1"))
          .isInstanceOfSatisfying(
              PaymentException.class,
              error -> {
                assertThat(error.code()).isEqualTo("BOOKING_UNAVAILABLE");
                assertThat(error.status().value()).isEqualTo(503);
              });
      server.verify();
      verify(loadBalancer)
          .execute(
              eq("booking-service"),
              org.mockito.ArgumentMatchers.<LoadBalancerRequest<ClientHttpResponse>>any());
    } catch (java.io.IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    } finally {
      fixtures.close();
    }
  }

  @Test
  void rejectsInvalidSnapshotResponses() {
    try {
      for (String response :
          new String[] {
            "",
            "{}",
            "{bad-json",
            snapshotJson.replace("\"total\":\"9999999999999999999\"", "\"total\":1.5")
          }) {
        server.reset();
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.preparePurchaseSnapshot(orderId, "buyer-1"))
            .isInstanceOfSatisfying(
                PaymentException.class,
                ex -> {
                  assertThat(ex.code()).isEqualTo("BOOKING_UNAVAILABLE");
                  assertThat(ex.status().value()).isEqualTo(503);
                });
        server.verify();
      }
    } finally {
      fixtures.close();
    }
  }

  @Test
  void mapsRejectedAndUnavailableBookingResponses() {
    try {
      for (int status : new int[] {401, 403, 404, 409, 503}) {
        server.reset();
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        assertThatThrownBy(() -> client.preparePurchaseSnapshot(orderId, "buyer-1"))
            .isInstanceOfSatisfying(
                PaymentException.class,
                ex ->
                    assertThat(ex.status().value())
                        .isEqualTo(status == 404 ? 404 : status == 409 ? 409 : 503));
      }
      server.reset();
      server
          .expect(anything())
          .andRespond(withException(new java.net.SocketTimeoutException("test timeout")));
      assertThatThrownBy(() -> client.preparePurchaseSnapshot(orderId, "buyer-1"))
          .isInstanceOfSatisfying(
              PaymentException.class, ex -> assertThat(ex.status().value()).isEqualTo(503));
    } finally {
      fixtures.close();
    }
  }
}
