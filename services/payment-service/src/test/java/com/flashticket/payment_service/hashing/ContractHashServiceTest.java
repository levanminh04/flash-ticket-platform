package com.flashticket.payment_service.hashing;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.PaymentProvider;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import com.flashticket.payment_service.validation.PurchaseSnapshotValidator;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

class ContractHashServiceTest {
  static final PaymentContractFixtures F = new PaymentContractFixtures();
  final ContractHashService hashes =
      new ContractHashService(
          new CanonicalContractSerializer(F.factory.getValidator()),
          new PurchaseSnapshotValidator(F.factory.getValidator()));

  @AfterAll
  static void close() {
    F.close();
  }

  static String fixture(String n) throws Exception {
    return PaymentContractFixtures.resource("canonical-v1/" + n);
  }

  @Test
  void matchesGoldenHashesAndLowercaseHex() throws Exception {
    assertThat(
            hashes.requestHash(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                PaymentProvider.VNPAY,
                "buyer-1"))
        .isEqualTo("da056e90a7f9c56e2f743714e9f31aa41fad549341cc5588282b6debba79a05e");
    var p = F.codec.read(fixture("purchase-seated.input.json"), PurchaseSnapshot.class);
    assertThat(hashes.purchaseHash(p))
        .isEqualTo(fixture("purchase-seated.sha256"))
        .matches("[0-9a-f]{64}");
    var e =
        F.codec.read(
            fixture("start-envelope.input.json"), new TypeReference<Envelope<StartPayment>>() {});
    assertThat(hashes.envelopeHash(e)).isEqualTo(fixture("start-envelope.sha256"));
  }

  @Test
  void hashesBusinessChangesButIgnoresPurchaseArrayOrder() throws Exception {
    var node = F.quantity();
    var original = hashes.purchaseHash(F.purchase(node));
    var items = node.withArray("items");
    items.add(items.remove(0));
    assertThat(hashes.purchaseHash(F.purchase(node))).isEqualTo(original);
    node.put("discount", "0").put("total", "250").putNull("promotion");
    assertThat(hashes.purchaseHash(F.purchase(node))).isNotEqualTo(original);
    node.put("total", "249");
    assertThatThrownBy(() -> hashes.purchaseHash(F.purchase(node)))
        .isInstanceOf(ContractValidationException.class);
  }

  @Test
  void hashesAllEnvelopeMetadataAndKeepsRequestHashDomainSmall() throws Exception {
    var node = (ObjectNode) F.mapper.readTree(fixture("start-envelope.input.json"));
    var type = new TypeReference<Envelope<StartPayment>>() {};
    var original = F.codec.fromTree(node, type);
    var originalHash = hashes.envelopeHash(original);
    for (var field :
        new String[] {"occurredAt", "aggregateVersion", "causationId", "traceparent"}) {
      var changed = node.deepCopy();
      changed.put(
          field,
          switch (field) {
            case "occurredAt" -> "2030-01-01T01:00:01Z";
            case "aggregateVersion" -> "2";
            case "causationId" -> "10000000-0000-0000-0000-000000000099";
            default -> "00-00000000000000000000000000000001-0000000000000001-01";
          });
      assertThat(hashes.envelopeHash(F.codec.fromTree(changed, type))).isNotEqualTo(originalHash);
    }
    var p = original.data().purchase();
    String requestHash = hashes.requestHash(p.orderId(), PaymentProvider.VNPAY, p.buyerSubject());
    ((ObjectNode) node.get("data"))
        .put("requestKey", UUID.randomUUID().toString())
        .put("requestId", UUID.randomUUID().toString());
    var changed = F.codec.fromTree(node, type);
    assertThat(
            hashes.requestHash(
                changed.data().purchase().orderId(),
                changed.data().provider(),
                changed.data().purchase().buyerSubject()))
        .isEqualTo(requestHash);
    assertThat(hashes.requestHash(UUID.randomUUID(), PaymentProvider.VNPAY, p.buyerSubject()))
        .isNotEqualTo(requestHash);
    assertThat(hashes.requestHash(p.orderId(), PaymentProvider.VNPAY, " Buyer-1 "))
        .isNotEqualTo(requestHash);
    var purchaseTree = (ObjectNode) node.get("data").get("purchase");
    purchaseTree.withArray("items").add(purchaseTree.withArray("items").remove(0));
    ((ObjectNode) purchaseTree.withArray("items").get(1))
        .withArray("seats")
        .add(((ObjectNode) purchaseTree.withArray("items").get(1)).withArray("seats").remove(0));
    // Compare to the envelope with the same changed request keys, before reordering.
    assertThat(hashes.envelopeHash(F.codec.fromTree(node, type)))
        .isEqualTo(hashes.envelopeHash(changed));
  }

  @Test
  void rejectsMissingRequestIdentity() {
    var id = UUID.randomUUID();
    assertThatThrownBy(() -> hashes.requestHash(null, PaymentProvider.VNPAY, "buyer"))
        .isInstanceOf(ContractValidationException.class);
    assertThatThrownBy(() -> hashes.requestHash(id, null, "buyer"))
        .isInstanceOf(ContractValidationException.class);
    assertThatThrownBy(() -> hashes.requestHash(id, PaymentProvider.VNPAY, " "))
        .isInstanceOf(ContractValidationException.class);
    assertThatThrownBy(() -> hashes.requestHash(id, PaymentProvider.VNPAY, null))
        .isInstanceOf(ContractValidationException.class);
  }
}
