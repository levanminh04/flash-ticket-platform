package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.hashing.CanonicalContractSerializer;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.validation.MessageInvariantValidator;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import com.flashticket.payment_service.validation.PurchaseSnapshotValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.type.TypeReference;

@org.springframework.context.annotation.Import(
    com.flashticket.payment_service.config.PaymentReadRepositoryTestConfiguration.class)
@SpringBootTest
class PaymentContractComponentsTest {
  @Autowired ContractCodec codec;
  @Autowired PurchaseSnapshotValidator purchases;
  @Autowired CanonicalContractSerializer serializer;
  @Autowired ContractHashService hashes;
  @Autowired MessageInvariantValidator messages;

  @Test
  void validatesAndHashesUsingRealSpringBeans() throws Exception {
    var json = PaymentContractFixtures.resource("canonical-v1/start-envelope.input.json");
    var envelope = codec.read(json, new TypeReference<Envelope<StartPayment>>() {});
    messages.validateStartPayment(envelope);
    var purchase = envelope.data().purchase();
    assertThat(purchases.expectedTicketCount(purchase)).isEqualTo(3);
    assertThat(hashes.purchaseHash(purchase))
        .isEqualTo(PaymentContractFixtures.resource("canonical-v1/purchase-seated.sha256"));
    assertThat(hashes.envelopeHash(envelope))
        .isEqualTo(PaymentContractFixtures.resource("canonical-v1/start-envelope.sha256"));
    assertThat(
            hashes.requestHash(
                purchase.orderId(), envelope.data().provider(), purchase.buyerSubject()))
        .isEqualTo(envelope.data().requestHash());
    assertThat(
            new String(serializer.purchaseBytes(purchase), java.nio.charset.StandardCharsets.UTF_8))
        .isEqualTo(PaymentContractFixtures.resource("canonical-v1/purchase-seated.canonical.json"));
    var bad =
        codec.read(
            json.replace("\"total\": \"225\"", "\"total\": \"224\""),
            new TypeReference<Envelope<StartPayment>>() {});
    assertThatThrownBy(() -> messages.validateStartPayment(bad))
        .isInstanceOf(ContractValidationException.class);
  }
}
