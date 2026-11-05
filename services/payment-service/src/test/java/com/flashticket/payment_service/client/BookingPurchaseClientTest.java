package com.flashticket.payment_service.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.flashticket.payment_service.config.PaymentRuntimeConfig;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
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
