package com.flashticket.payment_service.validation;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.EventCancellation;
import com.flashticket.payment_service.dto.message.FinanceConfiguration;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.hashing.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

class MessageInvariantValidatorTest {
  static final PaymentContractFixtures F = new PaymentContractFixtures();
  final PurchaseSnapshotValidator purchases =
      new PurchaseSnapshotValidator(F.factory.getValidator());
  final ContractHashService hashes =
      new ContractHashService(new CanonicalContractSerializer(F.factory.getValidator()), purchases);
  final MessageInvariantValidator validator = new MessageInvariantValidator(purchases, hashes);
  static final TypeReference<Envelope<StartPayment>> TYPE = new TypeReference<>() {};

  @AfterAll
  static void close() {
    F.close();
  }

  ObjectNode tree() throws Exception {
    return (ObjectNode)
        F.mapper.readTree(
            PaymentContractFixtures.resource("canonical-v1/start-envelope.input.json"));
  }

  @Test
  void acceptsValidCommandWithoutInventingVersionEquality() throws Exception {
    var node = tree();
    node.put("aggregateVersion", "2");
    assertThatCode(() -> validator.validateStartPayment(F.codec.fromTree(node, TYPE)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsEachBrokenEnvelopeLink() throws Exception {
    for (var sample :
        new String[][] {
          {"messageType", "bad", "$.messageType", "MESSAGE_TYPE_MISMATCH"},
          {
            "messageId",
            "10000000-0000-0000-0000-000000000099",
            "$.messageId",
            "MESSAGE_ID_MISMATCH"
          },
          {
            "aggregateId",
            "10000000-0000-0000-0000-000000000099",
            "$.aggregateId",
            "AGGREGATE_ID_MISMATCH"
          },
          {
            "correlationId",
            "10000000-0000-0000-0000-000000000099",
            "$.correlationId",
            "CORRELATION_ID_MISMATCH"
          }
        }) {
      var node = tree();
      node.put(sample[0], sample[1]);
      check(node, sample[2], sample[3]);
    }
  }

  @Test
  void rejectsFakeHashAndInvalidPurchase() throws Exception {
    var node = tree();
    ((ObjectNode) node.get("data")).put("requestHash", "fixture");
    check(node, "$.data.requestHash", "REQUEST_HASH_MISMATCH");
    node = tree();
    ((ObjectNode) node.get("data").get("purchase")).put("total", "224");
    check(node, "$.total", "TOTAL_MISMATCH");
    assertThatThrownBy(() -> validator.validateStartPayment(null))
        .isInstanceOf(ContractValidationException.class);
  }

  void check(ObjectNode node, String field, String code) {
    var ex = catchThrowable(() -> validator.validateStartPayment(F.codec.fromTree(node, TYPE)));
    assertThat(ex).isInstanceOf(ContractValidationException.class);
    assertThat(((ContractValidationException) ex).field()).isEqualTo(field);
    assertThat(((ContractValidationException) ex).code()).isEqualTo(code);
  }

  @Test
  void rejectsEventCancellationForDifferentAggregate() throws Exception {
    var cancellation =
        F.codec.read(
            PaymentContractFixtures.resource("EventCancellation.json"), EventCancellation.class);
    var messageId = UUID.randomUUID();
    var correlationId = UUID.randomUUID();
    var occurredAt = Instant.now();
    var envelope =
        new Envelope<>(
            messageId,
            "event.cancelled.v1",
            1,
            cancellation.eventId(),
            "1",
            occurredAt,
            correlationId,
            null,
            null,
            cancellation);
    assertThatCode(() -> validator.validateEventCancellation(envelope)).doesNotThrowAnyException();
    var mismatched =
        new Envelope<>(
            messageId,
            "event.cancelled.v1",
            1,
            UUID.randomUUID(),
            "1",
            occurredAt,
            correlationId,
            null,
            null,
            cancellation);
    var ex = catchThrowable(() -> validator.validateEventCancellation(mismatched));
    assertThat(ex).isInstanceOf(ContractValidationException.class);
    assertThat(((ContractValidationException) ex).field()).isEqualTo("$");
    assertThat(((ContractValidationException) ex).code()).isEqualTo("EVENT_CANCELLATION_MISMATCH");
  }

  @Test
  void rejectsFinanceConfigurationForDifferentAggregate() {
    var configuration =
        new FinanceConfiguration(UUID.randomUUID(), "organizer", Instant.now(), "0.1", "3", "VND");
    var messageId = UUID.randomUUID();
    var correlationId = UUID.randomUUID();
    var occurredAt = Instant.now();
    var envelope =
        new Envelope<>(
            messageId,
            "event.finance-configured.v1",
            1,
            configuration.eventId(),
            "1",
            occurredAt,
            correlationId,
            null,
            null,
            configuration);
    assertThatCode(() -> validator.validateFinanceConfiguration(envelope))
        .doesNotThrowAnyException();
    var mismatched =
        new Envelope<>(
            messageId,
            "event.finance-configured.v1",
            1,
            UUID.randomUUID(),
            "1",
            occurredAt,
            correlationId,
            null,
            null,
            configuration);
    var ex = catchThrowable(() -> validator.validateFinanceConfiguration(mismatched));
    assertThat(ex).isInstanceOf(ContractValidationException.class);
    assertThat(((ContractValidationException) ex).field()).isEqualTo("$");
    assertThat(((ContractValidationException) ex).code())
        .isEqualTo("FINANCE_CONFIGURATION_MISMATCH");
  }
}
