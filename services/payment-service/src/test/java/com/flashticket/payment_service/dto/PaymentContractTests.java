package com.flashticket.payment_service.dto;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.config.PaymentJsonConfig;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import jakarta.validation.Validator;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PaymentContractTests {
  @Test
  void readsBookingWireFixturesAndPreservesNulls() throws Exception {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(PaymentJsonConfig.class)
        .run(
            context -> {
              var mapper = context.getBean(JsonMapper.class);
              var validator = context.getBean(Validator.class);
              for (String file :
                  List.of(
                      "purchase-quantity.json", "purchase-seated.json", "purchase-standing.json")) {
                var snapshot =
                    mapper.readValue(
                        Path.of("src/test/resources/fixtures/payment", file).toFile(),
                        PurchaseSnapshot.class);
                assertThat(validator.validate(snapshot)).isEmpty();
                var tree = mapper.valueToTree(snapshot);
                assertThat(tree.get("venueName").isNull()).isTrue();
                assertThat(tree.get("promotion").isNull()).isTrue();
                assertThat(tree.has("validSnapshot")).isFalse();
                assertThatThrownBy(() -> snapshot.getItems().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
              }
              for (var pair :
                  List.of("start-payment-envelope.json", "event-cancelled-envelope.json")) {
                var type =
                    mapper
                        .getTypeFactory()
                        .constructParametricType(
                            Envelope.class,
                            pair.startsWith("start")
                                ? StartPayment.class
                                : EventCancellation.class);
                Object message =
                    mapper.readValue(
                        Path.of("src/test/resources/fixtures/payment", pair).toFile(), type);
                assertThat(validator.validate(message)).isEmpty();
              }
            });
  }

  @Test
  void rejectsUnknownMissingAndCoercedFields() throws Exception {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(PaymentJsonConfig.class)
        .run(
            context -> {
              var mapper = context.getBean(JsonMapper.class);
              ObjectNode original =
                  (ObjectNode)
                      mapper.readTree(
                          Path.of("src/test/resources/fixtures/payment/purchase-quantity.json")
                              .toFile());
              ObjectNode unknown = original.deepCopy();
              unknown.put("extra", "unexpected");
              assertThatThrownBy(() -> mapper.treeToValue(unknown, PurchaseSnapshot.class))
                  .isInstanceOf(RuntimeException.class);
              ObjectNode missing = original.deepCopy();
              missing.remove("promotion");
              assertThatThrownBy(() -> mapper.treeToValue(missing, PurchaseSnapshot.class))
                  .isInstanceOf(RuntimeException.class);
              for (String field : List.of("total", "sourceOrderVersion")) {
                ObjectNode coerced = original.deepCopy();
                coerced.put(field, 200);
                assertThatThrownBy(() -> mapper.treeToValue(coerced, PurchaseSnapshot.class))
                    .isInstanceOf(RuntimeException.class);
              }
              ObjectNode fractional = original.deepCopy();
              ((ObjectNode) fractional.get("items").get(0)).put("quantity", 1.5);
              assertThatThrownBy(() -> mapper.treeToValue(fractional, PurchaseSnapshot.class))
                  .isInstanceOf(RuntimeException.class);
              ObjectNode ordinal = original.deepCopy();
              ordinal.put("currency", 0);
              assertThatThrownBy(() -> mapper.treeToValue(ordinal, PurchaseSnapshot.class))
                  .isInstanceOf(RuntimeException.class);
            });
  }

  @Test
  void validatesPurchaseAndCancellationConsistency() throws Exception {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(PaymentJsonConfig.class)
        .run(
            context -> {
              var mapper = context.getBean(JsonMapper.class);
              var validator = context.getBean(Validator.class);
              ObjectNode purchase =
                  (ObjectNode)
                      mapper.readTree(
                          Path.of("src/test/resources/fixtures/payment/purchase-seated.json")
                              .toFile());
              for (String total : List.of("199", "200.1", "2e2", "-1", "10000000000000000000")) {
                ObjectNode bad = purchase.deepCopy();
                bad.put("total", total);
                assertThat(validator.validate(mapper.treeToValue(bad, PurchaseSnapshot.class)))
                    .isNotEmpty();
              }
              ObjectNode repeated = purchase.deepCopy();
              ((ObjectNode) repeated.get("items").get(0).get("seats").get(1))
                  .put(
                      "seatId",
                      repeated.get("items").get(0).get("seats").get(0).get("seatId").asString());
              assertThat(validator.validate(mapper.treeToValue(repeated, PurchaseSnapshot.class)))
                  .isNotEmpty();
              ObjectNode cancellation =
                  (ObjectNode)
                      mapper
                          .readTree(
                              Path.of(
                                      "src/test/resources/fixtures/payment/event-cancelled-envelope.json")
                                  .toFile())
                          .get("data");
              assertThat(
                      validator.validate(mapper.treeToValue(cancellation, EventCancellation.class)))
                  .isEmpty();
              ((ObjectNode) cancellation.get("access")).put("sourceConfigVersion", "2");
              assertThat(
                      validator.validate(mapper.treeToValue(cancellation, EventCancellation.class)))
                  .isNotEmpty();
              cancellation =
                  (ObjectNode)
                      mapper
                          .readTree(
                              Path.of(
                                      "src/test/resources/fixtures/payment/event-cancelled-envelope.json")
                                  .toFile())
                          .get("data");
              ((ObjectNode) cancellation.get("sales")).putArray("ticketTypes");
              ((ObjectNode) cancellation.get("sales")).putArray("sectors");
              ((ObjectNode) cancellation.get("sales")).putArray("seats");
              assertThat(
                      validator.validate(mapper.treeToValue(cancellation, EventCancellation.class)))
                  .isEmpty();
            });
  }

  @Test
  void validatesEnvelopeAndResultVariants() throws Exception {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(PaymentJsonConfig.class)
        .run(
            context -> {
              var mapper = context.getBean(JsonMapper.class);
              var validator = context.getBean(Validator.class);
              var cases =
                  mapper.readTree(
                      Path.of("src/test/resources/fixtures/payment/message-cases.json").toFile());
              for (var node : cases) {
                Class<?> payload =
                    switch (node.get("messageType").asString()) {
                      case "event.finance-configured.v1" -> FinanceConfiguration.class;
                      case "booking.accept-payment.v1" -> AcceptPayment.class;
                      case "booking.payment-decision.v1" -> PaymentDecision.class;
                      case "ticket.issue.v1" -> IssueTickets.class;
                      case "ticket.issuance-result.v1" -> IssuanceResult.class;
                      case "booking.order-completed.v1" -> OrderCompleted.class;
                      case "booking.issuance-result.v1" -> BookingIssuanceResult.class;
                      default -> throw new AssertionError("Unexpected fixture type");
                    };
                var type = mapper.getTypeFactory().constructParametricType(Envelope.class, payload);
                Object message = mapper.readValue(node.toString(), type);
                assertThat(validator.validate(message)).isEmpty();
                ObjectNode bad = (ObjectNode) node.deepCopy();
                bad.put("schemaVersion", 2);
                assertThat(validator.validate(mapper.readValue(bad.toString(), type))).isNotEmpty();
              }
              var id = UUID.randomUUID();
              var attempt = UUID.randomUUID();
              assertThat(
                      validator.validate(
                          new StartPaymentResult(
                              id,
                              id,
                              com.flashticket.payment_service.enums.StartPaymentResultState.CREATED,
                              attempt,
                              null)))
                  .isEmpty();
              assertThat(
                      validator.validate(
                          new StartPaymentResult(
                              id,
                              id,
                              com.flashticket.payment_service.enums.StartPaymentResultState
                                  .REJECTED,
                              null,
                              "ORDER_EXPIRED")))
                  .isEmpty();
              assertThat(
                      validator.validate(
                          new StartPaymentResult(
                              id,
                              id,
                              com.flashticket.payment_service.enums.StartPaymentResultState.CREATED,
                              null,
                              null)))
                  .isNotEmpty();
              ObjectNode start =
                  (ObjectNode)
                      mapper.readTree(
                          Path.of("src/test/resources/fixtures/payment/start-payment-envelope.json")
                              .toFile());
              start.put("messageId", UUID.randomUUID().toString());
              Object wrong =
                  mapper.readValue(
                      start.toString(),
                      mapper
                          .getTypeFactory()
                          .constructParametricType(Envelope.class, StartPayment.class));
              assertThat(validator.validate(wrong)).isNotEmpty();
            });
  }

  @Test
  void preservesExactMoneyVersionAndHiddenProperty() throws Exception {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(PaymentJsonConfig.class)
        .run(
            context -> {
              var mapper = context.getBean(JsonMapper.class);
              var validator = context.getBean(Validator.class);
              ObjectNode purchase =
                  (ObjectNode)
                      mapper.readTree(
                          Path.of("src/test/resources/fixtures/payment/purchase-quantity.json")
                              .toFile());
              purchase.put("sourceOrderVersion", "9007199254740993");
              var snapshot = mapper.treeToValue(purchase, PurchaseSnapshot.class);
              assertThat(validator.validate(snapshot)).isEmpty();
              assertThat(mapper.valueToTree(snapshot).get("sourceOrderVersion").asString())
                  .isEqualTo("9007199254740993");
              ObjectNode cancellation =
                  (ObjectNode)
                      mapper
                          .readTree(
                              Path.of(
                                      "src/test/resources/fixtures/payment/event-cancelled-envelope.json")
                                  .toFile())
                          .get("data");
              var roundtrip =
                  mapper.valueToTree(mapper.treeToValue(cancellation, EventCancellation.class));
              var seat = roundtrip.get("sales").get("seats").get(0);
              assertThat(seat.has("isHidden")).isTrue();
              assertThat(seat.has("hidden")).isFalse();
              assertThat(
                      roundtrip
                          .get("sales")
                          .get("promotions")
                          .get(0)
                          .get("discount")
                          .get("kind")
                          .asString())
                  .isEqualTo("FIXED");
              ObjectNode promotion =
                  (ObjectNode) cancellation.get("sales").get("promotions").get(0);
              promotion.set(
                  "discount", mapper.createObjectNode().put("kind", "PERCENT").put("percent", 10));
              assertThat(
                      validator.validate(mapper.treeToValue(cancellation, EventCancellation.class)))
                  .isEmpty();
            });
  }
}
