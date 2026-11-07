package com.flashticket.payment_service.dto;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ContractCodecTest {
  static final jakarta.validation.ValidatorFactory FACTORY =
      Validation.buildDefaultValidatorFactory();
  static final ContractCodec CODEC =
      new ContractCodec(JsonMapper.builder().build(), FACTORY.getValidator());

  @Test
  void selectsFirstConstraintPathForDirectObjects() throws Exception {
    var mapper = JsonMapper.builder().build();
    var tree = (ObjectNode) mapper.readTree(purchaseJson());
    tree.put("buyerSubject", "").put("eventTitle", "");
    var purchase = mapper.treeToValue(tree, PurchaseSnapshot.class);

    assertThatThrownBy(() -> CODEC.toTree(purchase))
        .isInstanceOfSatisfying(
            ContractValidationException.class,
            error -> {
              assertThat(error.field()).isEqualTo("buyerSubject");
              assertThat(error.code()).isEqualTo("CONSTRAINT_VIOLATION");
            });
  }

  @Test
  void keepsRequiredErrorForNullObjects() {
    assertThatThrownBy(() -> CODEC.toTree(null))
        .isInstanceOfSatisfying(
            ContractValidationException.class,
            error -> {
              assertThat(error.field()).isEqualTo("$");
              assertThat(error.code()).isEqualTo("REQUIRED");
            });
  }

  public static String purchaseJson() throws Exception {
    try (var in = ContractCodecTest.class.getResourceAsStream("/contracts/purchase.json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "FinanceConfiguration",
        "EventCancellation",
        "StartPayment",
        "StartPaymentResult",
        "AcceptPayment",
        "PaymentDecision",
        "IssueTickets",
        "IssuanceResult",
        "OrderCompleted",
        "BookingIssuanceResult"
      })
  void roundTripsAllPaymentMessageFixtures(String name) throws Exception {
    try (var in = getClass().getResourceAsStream("/contracts/" + name + ".json")) {
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Class<?> type = Class.forName("com.flashticket.payment_service.dto.message." + name);
      var object = CODEC.read(json, type);
      var mapper = JsonMapper.builder().build();
      assertThat(mapper.readTree(CODEC.write(object))).isEqualTo(mapper.readTree(json));
    }
  }

  @Test
  void preservesSnapshotWireFormat() throws Exception {
    var purchase = CODEC.read(purchaseJson(), PurchaseSnapshot.class);
    assertThat(purchase.total()).isEqualTo("9999999999999999999");
    assertThat(purchase.sourceOrderVersion()).isEqualTo("9223372036854775807");
    var tree = CODEC.toTree(purchase);
    assertThat(tree.get("total").isString()).isTrue();
    assertThat(tree.get("frozenAt").asString()).isEqualTo("2030-01-01T01:00:00Z");
    assertThat(tree.has("venueName")).isTrue();
    assertThat(tree.get("venueName").isNull()).isTrue();
    assertThat(CODEC.read(CODEC.write(purchase), PurchaseSnapshot.class)).isEqualTo(purchase);
    var rules =
        new com.flashticket.payment_service.validation.PurchaseSnapshotValidator(
            FACTORY.getValidator());
    var invariants =
        new com.flashticket.payment_service.validation.MessageInvariantValidator(
            rules,
            new com.flashticket.payment_service.hashing.ContractHashService(
                new com.flashticket.payment_service.hashing.CanonicalContractSerializer(
                    FACTORY.getValidator()),
                rules));
    for (var mode : java.util.List.of("quantity", "standing", "seated")) {
      for (boolean promotion : new boolean[] {false, true}) {
        try (var in =
            getClass()
                .getResourceAsStream(
                    "/contracts/compatibility/snapshot-"
                        + mode
                        + (promotion ? "-promotion" : "")
                        + ".json")) {
          assertThat(in).isNotNull();
          var captured =
              CODEC.read(
                  new String(in.readAllBytes(), StandardCharsets.UTF_8), PurchaseSnapshot.class);
          rules.validate(captured);
          assertThat(captured.sourceOrderVersion()).isEqualTo("1");
          assertThat(CODEC.toTree(captured).get("total").isString()).isTrue();
          assertThat(CODEC.read(CODEC.write(captured), PurchaseSnapshot.class)).isEqualTo(captured);
        }
      }
    }
    for (var state : java.util.List.of("completed", "failed")) {
      try (var in =
          getClass()
              .getResourceAsStream("/contracts/compatibility/ticket-result-" + state + ".json")) {
        assertThat(in).isNotNull();
        var result =
            CODEC.readEnvelope(
                new String(in.readAllBytes(), StandardCharsets.UTF_8),
                com.flashticket.payment_service.dto.message.IssuanceResult.class);
        invariants.validateIssuanceResult(result);
        assertThat(result.aggregateVersion()).isEqualTo("1");
        assertThat(result.data().state().name())
            .isEqualTo(state.toUpperCase(java.util.Locale.ROOT));
      }
    }
  }

  @Test
  void keepsGenericEnvelopeDataTyped() throws Exception {
    String json =
        """
    {"messageId":"10000000-0000-0000-0000-000000000005","messageType":"payment.start-requested.v1","schemaVersion":1,
    "aggregateId":"10000000-0000-0000-0000-000000000001","aggregateVersion":"1","occurredAt":"2030-01-01T01:00:00Z",
    "correlationId":"10000000-0000-0000-0000-000000000001","causationId":null,"traceparent":null,
    "data":{"requestId":"10000000-0000-0000-0000-000000000005","requestKey":"10000000-0000-0000-0000-000000000006","requestHash":"fixture","provider":"VNPAY","purchase":%s}}
    """
            .formatted(purchaseJson());
    Envelope<StartPayment> envelope =
        CODEC.read(json, new TypeReference<Envelope<StartPayment>>() {});
    assertThat(envelope.data().purchase().buyerSubject()).isEqualTo("buyer-1");
    assertThat(CODEC.toTree(envelope).get("data").get("purchase").get("total").asString())
        .isEqualTo("9999999999999999999");
  }
}
