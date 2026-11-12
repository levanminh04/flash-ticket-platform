package com.flashticket.payment_service.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.flashticket.payment_service.config.PaymentRuntimeConfig;
import com.flashticket.payment_service.config.VNPayConfig;
import com.flashticket.payment_service.dto.response.RefundGatewayRequest;
import com.flashticket.payment_service.entity.enums.RefundGatewayOutcome;
import com.flashticket.payment_service.util.VNPayUtil;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RestClientTest(VNPayRefundClient.class)
@Import({PaymentRuntimeConfig.class, VNPayConfig.class})
class VNPayRefundClientTest {
  @MockitoBean OAuth2AuthorizedClientManager authorizedClientManager;
  @MockitoBean PlatformTransactionManager transactionManager;
  @MockitoBean org.springframework.cloud.client.loadbalancer.LoadBalancerClient loadBalancer;
  @Autowired VNPayConfig vnPayConfig;
  final JsonMapper json = JsonMapper.builder().build();
  final List<String> refundFields =
      List.of(
          "vnp_ResponseId",
          "vnp_Command",
          "vnp_ResponseCode",
          "vnp_Message",
          "vnp_TmnCode",
          "vnp_TxnRef",
          "vnp_Amount",
          "vnp_BankCode",
          "vnp_PayDate",
          "vnp_TransactionNo",
          "vnp_TransactionType",
          "vnp_TransactionStatus",
          "vnp_OrderInfo");
  VNPayRefundClient client;
  MockRestServiceServer server;
  RefundGatewayRequest request;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl(vnPayConfig.getApiUrl());
    server = MockRestServiceServer.bindTo(builder).build();
    client = new VNPayRefundClient(builder.build(), vnPayConfig);
    request =
        new RefundGatewayRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "0123456789abcdef0123456789abcdef",
            "txn-1",
            new BigDecimal("12345"),
            "transaction-1",
            "20261102123045");
  }

  @Test
  void productionVnpayClientDoesNotUseBookingDiscoveryOrBearerToken() {
    var config = new PaymentRuntimeConfig();
    org.springframework.test.util.ReflectionTestUtils.setField(
        config, "bookingDiscoveryEnabled", true);
    org.springframework.test.util.ReflectionTestUtils.setField(
        config, "connectTimeout", java.time.Duration.ofSeconds(2));
    org.springframework.test.util.ReflectionTestUtils.setField(
        config, "readTimeout", java.time.Duration.ofSeconds(2));
    org.springframework.test.util.ReflectionTestUtils.setField(
        config, "vnpayApiUrl", vnPayConfig.getApiUrl());
    org.springframework.test.util.ReflectionTestUtils.setField(
        config, "vnpayConnectTimeout", java.time.Duration.ofSeconds(2));
    org.springframework.test.util.ReflectionTestUtils.setField(
        config, "vnpayReadTimeout", java.time.Duration.ofSeconds(2));
    var sharedBuilder = RestClient.builder();
    config.bookingRestClient(sharedBuilder, authorizedClientManager, loadBalancer);
    var builder = config.vnpayRestClient(sharedBuilder).mutate();
    var probeServer = MockRestServiceServer.bindTo(builder).build();
    probeServer
        .expect(requestTo(vnPayConfig.getApiUrl() + "/health"))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));
    assertThat(
            builder
                .build()
                .get()
                .uri(vnPayConfig.getApiUrl() + "/health")
                .retrieve()
                .body(String.class))
        .isEqualTo("OK");
    probeServer.verify();
    org.mockito.Mockito.verifyNoInteractions(loadBalancer, authorizedClientManager);
  }

  @Test
  void signsRefundRequestInDocumentedOrder() {
    var captured = new AtomicReference<String>();
    server
        .expect(requestTo(vnPayConfig.getApiUrl()))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            httpRequest -> captured.set(((MockClientHttpRequest) httpRequest).getBodyAsString()))
        .andRespond(
            withSuccess(
                signedResponse(
                    Map.of("vnp_ResponseCode", "00", "vnp_TransactionStatus", "00"), refundFields),
                MediaType.APPLICATION_JSON));
    client.refund(request);
    var body = json.readTree(captured.get());
    assertThat(body.get("vnp_TransactionType").asText()).isEqualTo("02");
    assertThat(body.get("vnp_Amount").asText()).isEqualTo("1234500");
    assertThat(body.get("vnp_TransactionDate").asText()).isEqualTo(request.transactionDate());
    String hashData =
        String.join(
            "|",
            request.requestKey(),
            vnPayConfig.getVersion(),
            "refund",
            vnPayConfig.getTmnCode(),
            "02",
            request.txnRef(),
            "1234500",
            request.transactionNo(),
            request.transactionDate(),
            vnPayConfig.getRefundCreateBy(),
            body.get("vnp_CreateDate").asText(),
            vnPayConfig.getServerIp(),
            "Hoan tien " + request.refundId());
    assertThat(body.get("vnp_SecureHash").asText())
        .isEqualTo(VNPayUtil.hmacSHA512("fixture-secret", hashData));
    server.verify();
  }

  @Test
  void mapsRefundAndQueryResponseCodes() {
    String[][] cases = {
      {"refund", "00", "02", "00", "SUCCEEDED"},
      {"refund", "00", "02", "05", "PROCESSING"},
      {"refund", "94", "02", "", "PROCESSING"},
      {"refund", "95", "02", "", "FAILED"},
      {"refund", "99", "02", "", "UNKNOWN"},
      {"querydr", "00", "02", "00", "SUCCEEDED"},
      {"querydr", "00", "01", "00", "FAILED"}
    };
    for (String[] scenario : cases) {
      server.reset();
      var fieldOrder = new ArrayList<>(refundFields);
      boolean query = "querydr".equals(scenario[0]);
      if (query) {
        fieldOrder.add("vnp_PromotionCode");
        fieldOrder.add("vnp_PromotionAmount");
      }
      server
          .expect(anything())
          .andRespond(
              withSuccess(
                  signedResponse(
                      Map.of(
                          "vnp_Command",
                          scenario[0],
                          "vnp_ResponseCode",
                          scenario[1],
                          "vnp_TransactionType",
                          scenario[2],
                          "vnp_TransactionStatus",
                          scenario[3],
                          "vnp_TransactionNo",
                          "refund-1"),
                      fieldOrder),
                  MediaType.APPLICATION_JSON));
      var result = query ? client.queryRefund(request) : client.refund(request);
      assertThat(result.outcome()).isEqualTo(RefundGatewayOutcome.valueOf(scenario[4]));
      assertThat(result.providerRefundId()).isEqualTo("refund-1");
      assertThat(result.responseCode()).isEqualTo(scenario[1]);
      server.verify();
    }
  }

  @Test
  void treatsTimeoutAsUnknown() {
    for (boolean query : new boolean[] {false, true}) {
      server.reset();
      server.expect(anything()).andRespond(withException(new SocketTimeoutException()));
      var result = query ? client.queryRefund(request) : client.refund(request);
      assertThat(result.outcome()).isEqualTo(RefundGatewayOutcome.UNKNOWN);
      assertThat(result.providerRefundId()).isNull();
      assertThat(result.responseCode()).isEqualTo("GATEWAY_UNAVAILABLE");
      server.verify();
    }
  }

  @Test
  void rejectsResponseWithInvalidSignature() {
    for (boolean query : new boolean[] {false, true}) {
      server.reset();
      server
          .expect(anything())
          .andRespond(
              withSuccess(
                  "{\"vnp_ResponseCode\":\"00\",\"vnp_TransactionStatus\":\"00\","
                      + "\"vnp_TransactionType\":\"02\",\"vnp_SecureHash\":\"invalid\"}",
                  MediaType.APPLICATION_JSON));
      var result = query ? client.queryRefund(request) : client.refund(request);
      assertThat(result.outcome()).isEqualTo(RefundGatewayOutcome.UNKNOWN);
      assertThat(result.providerRefundId()).isNull();
      assertThat(result.responseCode()).isEqualTo("INVALID_SIGNATURE");
      server.verify();
    }
  }

  private String signedResponse(Map<String, String> fields, List<String> fieldOrder) {
    var response = new LinkedHashMap<>(fields);
    var values = new ArrayList<String>();
    for (String field : fieldOrder) {
      String value = fields.get(field);
      values.add(value == null ? "" : value);
    }
    response.put(
        "vnp_SecureHash", VNPayUtil.hmacSHA512("fixture-secret", String.join("|", values)));
    return json.writeValueAsString(response);
  }
}
