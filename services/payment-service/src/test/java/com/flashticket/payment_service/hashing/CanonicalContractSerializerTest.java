package com.flashticket.payment_service.hashing;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.IssueTickets;
import com.flashticket.payment_service.dto.message.PaymentProvider;
import com.flashticket.payment_service.dto.message.SalesConfiguration;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

class CanonicalContractSerializerTest {
  @Test
  void keepsValidSurrogatePairsAndUsesLowercaseControlEscapes() {
    var id = UUID.fromString("10000000-0000-0000-0000-000000000001");
    String actor = "buyer-" + Character.toString(0x1F3AB);
    assertThat(
            new String(
                serializer.requestBytes(id, PaymentProvider.VNPAY, actor), StandardCharsets.UTF_8))
        .contains(actor);
    String control = "buyer-" + (char) 0x001B;
    assertThat(
            new String(
                serializer.requestBytes(id, PaymentProvider.VNPAY, control),
                StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"actor\":\"buyer-\\u001b\",\"orderId\":\"10000000-0000-0000-0000-000000000001\",\"provider\":\"VNPAY\"}");
  }

  static final PaymentContractFixtures F = new PaymentContractFixtures();
  final CanonicalContractSerializer serializer =
      new CanonicalContractSerializer(F.factory.getValidator());

  @Test
  void selectsFirstConstraintPathBeforeCanonicalEncoding() throws Exception {
    var tree = F.quantity().put("buyerSubject", "").put("eventTitle", "");
    var purchase = F.mapper.treeToValue(tree, PurchaseSnapshot.class);

    assertThatThrownBy(() -> serializer.purchaseBytes(purchase))
        .isInstanceOfSatisfying(
            ContractValidationException.class,
            error -> {
              assertThat(error.field()).isEqualTo("buyerSubject");
              assertThat(error.code()).isEqualTo("CONSTRAINT_VIOLATION");
            });
  }

  @Test
  void keepsRequiredErrorForNullPurchases() {
    assertThatThrownBy(() -> serializer.purchaseBytes(null))
        .isInstanceOfSatisfying(
            ContractValidationException.class,
            error -> {
              assertThat(error.field()).isEqualTo("$");
              assertThat(error.code()).isEqualTo("REQUIRED");
            });
  }

  @AfterAll
  static void close() {
    F.close();
  }

  static String fixture(String n) throws Exception {
    return PaymentContractFixtures.resource("canonical-v1/" + n);
  }

  @Test
  void matchesIndependentGoldenBytes() throws Exception {
    var purchase = F.codec.read(fixture("purchase-seated.input.json"), PurchaseSnapshot.class);
    assertThat(serializer.purchaseBytes(purchase))
        .isEqualTo(fixture("purchase-seated.canonical.json").getBytes(StandardCharsets.UTF_8));
    var envelope =
        F.codec.read(
            fixture("start-envelope.input.json"), new TypeReference<Envelope<StartPayment>>() {});
    assertThat(serializer.envelopeBytes(envelope))
        .isEqualTo(fixture("start-envelope.canonical.json").getBytes(StandardCharsets.UTF_8));
    assertThat(
            serializer.requestBytes(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                PaymentProvider.VNPAY,
                "buyer-1"))
        .isEqualTo(
            "{\"actor\":\"buyer-1\",\"orderId\":\"10000000-0000-0000-0000-000000000001\",\"provider\":\"VNPAY\"}"
                .getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void sortsOnlyPurchaseCollectionsWithoutMutatingInput() throws Exception {
    var tree = (ObjectNode) F.mapper.readTree(fixture("purchase-seated.input.json"));
    var purchase = F.purchase(tree);
    var originalItems = purchase.items();
    var reversed = tree.withArray("items");
    var first = reversed.remove(0);
    reversed.add(first);
    var seats = ((ObjectNode) reversed.get(1)).withArray("seats");
    var seat = seats.remove(0);
    seats.add(seat);
    assertThat(serializer.purchaseBytes(F.purchase(tree)))
        .isEqualTo(serializer.purchaseBytes(purchase));
    assertThat(purchase.items()).containsExactlyElementsOf(originalItems);
    assertThat(purchase.items().get(0).seats().get(0).seatId().toString()).endsWith("000d");
  }

  @Test
  void preservesSubjectUnicodeAndWhitespaceWhileNormalizingUuidAndInstant() throws Exception {
    var tree = (ObjectNode) F.mapper.readTree(fixture("purchase-seated.input.json"));
    var upper = serializer.purchaseBytes(F.purchase(tree));
    tree.put("orderId", tree.get("orderId").asString().toLowerCase());
    tree.put("frozenAt", "2030-01-01T01:00:00Z");
    assertThat(serializer.purchaseBytes(F.purchase(tree))).isEqualTo(upper);
    tree.put("buyerSubject", " Buyer-1 ");
    assertThat(new String(serializer.purchaseBytes(F.purchase(tree)), StandardCharsets.UTF_8))
        .contains(" Buyer-1 ");
    tree.put("buyerSubject", "é");
    var composed = serializer.purchaseBytes(F.purchase(tree));
    tree.put("buyerSubject", "e\u0301");
    assertThat(serializer.purchaseBytes(F.purchase(tree))).isNotEqualTo(composed);
  }

  @Test
  void normalizesIssueTicketsPurchaseAndPreservesOtherArrays() throws Exception {
    var p = F.codec.read(fixture("purchase-seated.input.json"), PurchaseSnapshot.class);
    var command =
        new IssueTickets(p.orderId(), UUID.fromString("10000000-0000-0000-0000-000000000002"), p);
    var env =
        new Envelope<>(
            UUID.randomUUID(),
            "ticket.issue.v1",
            1,
            p.orderId(),
            "1",
            p.frozenAt(),
            p.orderId(),
            null,
            null,
            command);
    var tree = F.mapper.readTree(serializer.envelopeBytes(env));
    assertThat(tree.get("data").get("purchase").get("items").get(0).get("orderItemId").asString())
        .endsWith("000a");
    // SalesConfiguration array order is not purchase semantics.
    var salesTree =
        (ObjectNode)
            F.mapper
                .readTree(PaymentContractFixtures.resource("EventCancellation.json"))
                .get("sales");
    var promotions = salesTree.withArray("promotions");
    promotions.add(promotions.remove(0));
    var sales = F.codec.fromTree(salesTree, SalesConfiguration.class);
    var salesEnv =
        new Envelope<>(
            UUID.randomUUID(),
            "event.sales-configured.v1",
            1,
            p.eventId(),
            "1",
            p.frozenAt(),
            p.eventId(),
            null,
            null,
            sales);
    var result =
        F.mapper.readTree(serializer.envelopeBytes(salesEnv)).get("data").get("promotions");
    assertThat(result.get(0).get("code").asString()).isEqualTo("B");
    assertThat(result.get(1).get("code").asString()).isEqualTo("A");
  }
}
