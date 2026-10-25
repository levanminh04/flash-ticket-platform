package com.flashticket.payment_service.gateway.vnpay;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.payment_service.config.PaymentReadRepositoryTestConfiguration;
import com.flashticket.payment_service.gateway.*;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "flashticket.payment.vnpay.enabled=true",
      "flashticket.payment.vnpay.callbacks-enabled=true",
      "flashticket.payment.vnpay.tmn-code=TEST1234",
      "flashticket.payment.vnpay.hash-secret=key",
      "flashticket.payment.vnpay.return-url=https://merchant.example.test/api/v1/payments/vnpay/return",
      "flashticket.payment.vnpay.ipn-url=https://merchant.example.test/api/v1/payments/vnpay/ipn",
      "flashticket.payment.vnpay.sandbox-payer-ip=127.0.0.1",
      "flashticket.payment.vnpay.api-client-ip=127.0.0.1"
    })
@AutoConfigureMockMvc
@Import({PaymentReadRepositoryTestConfiguration.class, VnpayCallbackTest.Fixture.class})
class VnpayCallbackTest {
  static final String IPN = "/api/v1/payments/vnpay/ipn";
  @Autowired MockMvc mvc;
  @Autowired Handler handler;

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixture {
    @Bean
    Handler handler() {
      return new Handler();
    }
  }

  // Only HTTP/controller behavior is checked here, not PostgreSQL transactions.
  static class Handler implements VerifiedPaymentHandler {
    int calls;
    boolean fail;
    Outcome outcome = Outcome.RECORDED;

    public Outcome handle(ProviderPaymentResult result) {
      result.validateAgainst(VnpayTestFixtures.context());
      calls++;
      if (fail) throw new IllegalStateException("private-error-must-not-leak");
      return outcome;
    }
  }

  @BeforeEach
  void resetHandler() {
    handler.calls = 0;
    handler.fail = false;
    handler.outcome = VerifiedPaymentHandler.Outcome.RECORDED;
  }

  @Test
  void anonymousSignedIpnGetsAckOnlyAfterHandlerReturns() throws Exception {
    var uri = URI.create(IPN + "?" + VnpayTestFixtures.signed(VnpayTestFixtures.notification()));
    mvc.perform(get(uri)).andExpect(status().isOk()).andExpect(jsonPath("RspCode").value("00"));
    assertThat(handler.calls).isEqualTo(1);
    handler.outcome = VerifiedPaymentHandler.Outcome.ALREADY_RECORDED;
    mvc.perform(get(uri)).andExpect(jsonPath("RspCode").value("02"));
    handler.fail = true;
    mvc.perform(get(uri))
        .andExpect(jsonPath("RspCode").value("99"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-error"))));
  }

  @Test
  void badSignatureDuplicateKeysAndWrongAmountNeverGetSuccess() throws Exception {
    var signed = VnpayTestFixtures.signed(VnpayTestFixtures.notification());
    mvc.perform(get(URI.create(IPN + "?" + signed.replace("10000000", "10000100"))))
        .andExpect(jsonPath("RspCode").value("97"));
    mvc.perform(get(URI.create(IPN + "?" + signed + "&vnp_Amount=10000000")))
        .andExpect(jsonPath("RspCode").value("99"));
    assertThat(handler.calls).isZero();
    var m = VnpayTestFixtures.notification();
    m.put("vnp_Amount", "100");
    mvc.perform(get(URI.create(IPN + "?" + VnpayTestFixtures.signed(m))))
        .andExpect(jsonPath("RspCode").value("04"));
    assertThat(handler.calls).isZero();
  }

  @Test
  void returnIsNeutralReadOnlyAndOtherMethodsAndBuyerApisStayProtected() throws Exception {
    var raw = VnpayTestFixtures.signed(VnpayTestFixtures.notification());
    mvc.perform(get(URI.create("/api/v1/payments/vnpay/return?" + raw)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Đang kiểm tra")));
    assertThat(handler.calls).isZero();
    mvc.perform(post(IPN)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/orders/" + VnpayTestFixtures.context().orderId() + "/payment"))
        .andExpect(status().isUnauthorized());
  }
}
