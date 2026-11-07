package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.contract.TicketContracts;
import com.flashticket.ticket_service.contract.TicketContracts.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class TicketContractTests {
  private final JsonMapper json = JsonMapper.builder().build();
  private static final UUID ORDER = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID EVENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CHARGE = UUID.fromString("30000000-0000-0000-0000-000000000001");

  @Test
  void validatesFrozenPurchaseWithoutCallingAnotherOwner() {
    var envelope = issue();
    assertThatCode(() -> TicketContracts.validateIssue(envelope)).doesNotThrowAnyException();
    assertThat(TicketContracts.ticketCount(envelope.data().purchase())).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"01", "0", "-1", "1.0", "+1", "9223372036854775808"})
  void rejectsVersionsThatAreNotCanonicalPositivePostgresBigints(String value) {
    assertThatThrownBy(() -> TicketContracts.version(value))
        .isInstanceOf(InvalidContractException.class);
  }

  @Test
  void validatesEventProducerAccessAndRejectsMismatchedVersion() {
    var data =
        new AccessConfiguration(
            EVENT,
            "organizer",
            "PUBLISHED",
            time("2026-11-01T12:00:00Z"),
            time("2026-11-01T14:00:00Z"),
            "2");
    var valid = envelope("event.access-configured.v1", EVENT, "2", data);
    assertThatCode(() -> TicketContracts.validateAccess(valid)).doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                TicketContracts.validateAccess(
                    envelope("event.access-configured.v1", EVENT, "3", data)))
        .isInstanceOf(InvalidContractException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "subtotal",
        "discount",
        "total",
        "currency",
        "frozenAt",
        "sourceOrderVersion",
        "schemaVersion",
        "eventEndsAt",
        "customerEmail",
        "buyerSubject",
        "eventTitle",
        "mode"
      })
  void rejectsInvalidPurchaseInvariant(String field) {
    ObjectNode node = json.valueToTree(issue());
    ObjectNode purchase = (ObjectNode) node.path("data").path("purchase");
    switch (field) {
      case "subtotal" -> purchase.put(field, "201");
      case "discount" -> purchase.put(field, "1");
      case "total" -> purchase.put(field, "0");
      case "currency" -> purchase.put(field, "USD");
      case "frozenAt" -> purchase.put(field, "2026-10-30T12:01:00Z");
      case "sourceOrderVersion" -> purchase.put(field, "01");
      case "schemaVersion" -> purchase.put(field, 2);
      case "eventEndsAt" -> purchase.put(field, "2026-10-01T12:00:00Z");
      case "customerEmail" -> purchase.put(field, "invalid");
      case "buyerSubject", "eventTitle" -> purchase.put(field, " ");
      case "mode" -> purchase.put(field, "OTHER");
    }
    assertThatThrownBy(() -> TicketContracts.validateIssue(read(node)))
        .isInstanceOf(InvalidContractException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "unknown",
        "missing",
        "fractionalQuantity",
        "stringQuantity",
        "numericMoney",
        "badUuid",
        "offsetTimestamp",
        "missingNullable",
        "nullPrimitive",
        "nestedUnknown"
      })
  void strictDecoderRejectsInvalidWireShapesWithoutLeakingPayload(String mutation) {
    ObjectNode node = json.valueToTree(issue());
    ObjectNode purchase = (ObjectNode) node.path("data").path("purchase");
    ObjectNode item = (ObjectNode) purchase.path("items").get(0);
    switch (mutation) {
      case "unknown" -> node.put("secretPayload", "DO_NOT_LEAK");
      case "missing" -> purchase.remove("buyerSubject");
      case "fractionalQuantity" -> item.put("quantity", 2.5);
      case "stringQuantity" -> item.put("quantity", "2");
      case "numericMoney" -> item.put("unitPrice", 100);
      case "badUuid" -> purchase.put("eventId", "1-1-1-1-1");
      case "offsetTimestamp" -> purchase.put("frozenAt", "2026-10-30T19:00:00+07:00");
      case "missingNullable" -> purchase.remove("promotion");
      case "nullPrimitive" -> item.putNull("quantity");
      case "nestedUnknown" -> item.put("secretPayload", "DO_NOT_LEAK");
    }
    assertThatThrownBy(() -> read(node))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageNotContaining("DO_NOT_LEAK")
        .hasNoCause();
  }

  @Test
  void decoderRejectsRepeatedJsonPropertiesAndTrailingContent() {
    String raw = json.writeValueAsString(issue());
    assertThatThrownBy(
            () ->
                TicketContracts.decode(
                    json,
                    raw.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")
                        .getBytes(StandardCharsets.UTF_8),
                    IssueTickets.class))
        .isInstanceOf(InvalidContractException.class);
    assertThatThrownBy(
            () ->
                TicketContracts.decode(
                    json, (raw + " {}").getBytes(StandardCharsets.UTF_8), IssueTickets.class))
        .isInstanceOf(InvalidContractException.class);
  }

  @Test
  void canonicalHashIgnoresObjectFieldAndSelectionArrayOrderButIncludesCharge() {
    var first = issue().data();
    var item = first.purchase().items().getFirst();
    var other =
        new OrderItem(
            UUID.fromString("40000000-0000-0000-0000-000000000002"),
            UUID.randomUUID(),
            "VIP",
            1,
            "100",
            "100",
            null,
            List.of());
    ObjectNode original = json.valueToTree(first);
    var array = json.createArrayNode().add(json.valueToTree(other)).add(json.valueToTree(item));
    ((ObjectNode) original.path("purchase")).set("items", array);
    var reversed = original.deepCopy();
    ((ObjectNode) reversed.path("purchase"))
        .set(
            "items",
            json.createArrayNode().add(json.valueToTree(item)).add(json.valueToTree(other)));
    assertThat(TicketContracts.hash(json, original))
        .isEqualTo(TicketContracts.hash(json, reversed));
    assertThat(TicketContracts.inputHash(json, first))
        .isNotEqualTo(
            TicketContracts.inputHash(
                json, new IssueTickets(first.orderId(), UUID.randomUUID(), first.purchase())));
    assertThat(TicketContracts.hash(json, json.readTree("{\"b\":2,\"a\":1}")))
        .isEqualTo("43258cff783fe7036d8a43033f830adfc60ec037382473548ac742b888292777");
  }

  @Test
  void rejectsDuplicateItemsSeatsAndSelectionMismatches() {
    ObjectNode node = json.valueToTree(issue());
    ObjectNode purchase = (ObjectNode) node.path("data").path("purchase");
    var originalItem = purchase.path("items").get(0);
    purchase.set("items", json.createArrayNode().add(originalItem).add(originalItem));
    assertThatThrownBy(() -> TicketContracts.validateIssue(read(node)))
        .isInstanceOf(InvalidContractException.class);
    purchase.set("items", json.createArrayNode().add(originalItem));
    purchase.put("mode", "SEAT_MAP");
    purchase.put("sectorId", EVENT.toString());
    assertThatThrownBy(() -> TicketContracts.validateIssue(read(node)))
        .isInstanceOf(InvalidContractException.class);
  }

  @Test
  void validatesCancellationFromEventAndRejectsConflictingFullSnapshot() {
    var access =
        new AccessConfiguration(
            EVENT,
            "organizer",
            "CANCELLED",
            time("2026-11-01T12:00:00Z"),
            time("2026-11-01T14:00:00Z"),
            "3");
    var sales =
        json.createObjectNode()
            .put("schemaVersion", 1)
            .put("eventId", EVENT.toString())
            .put("sourceConfigVersion", "3")
            .put("eventStatus", "CANCELLED");
    var cancellation =
        new EventCancellation(
            EVENT,
            UUID.randomUUID(),
            "3",
            time("2026-10-30T12:00:00Z"),
            "organizer",
            sales,
            access);
    var envelope = envelope("event.cancelled.v1", EVENT, "3", cancellation);
    assertThatCode(() -> TicketContracts.validateCancellation(envelope)).doesNotThrowAnyException();
    ObjectNode raw = json.valueToTree(envelope);
    ((ObjectNode) raw.path("data").path("access")).put("sourceConfigVersion", "2");
    var conflicting =
        TicketContracts.decode(json, json.writeValueAsBytes(raw), EventCancellation.class);
    assertThatThrownBy(() -> TicketContracts.validateCancellation(conflicting))
        .isInstanceOf(InvalidContractException.class);
    sales.put("sourceConfigVersion", "9");
    assertThatCode(() -> TicketContracts.validateCancellation(envelope)).doesNotThrowAnyException();
  }

  @Test
  void validatesSeatedSnapshotAndRejectsDuplicateOrMissingSeat() {
    ObjectNode node = json.valueToTree(issue());
    ObjectNode purchase = (ObjectNode) node.path("data").path("purchase");
    ObjectNode item = (ObjectNode) purchase.path("items").get(0);
    purchase.put("mode", "SEAT_MAP").put("sectorId", EVENT.toString());
    item.put("sectorId", EVENT.toString());
    var seat =
        json.createObjectNode()
            .put("seatId", "60000000-0000-0000-0000-000000000001")
            .put("seatLabel", "A1")
            .put("rowName", "A")
            .put("seatNumber", "1");
    var other =
        seat.deepCopy()
            .put("seatId", "60000000-0000-0000-0000-000000000002")
            .put("seatLabel", "A2")
            .put("seatNumber", "2");
    item.set("seats", json.createArrayNode().add(seat).add(other));
    assertThatCode(() -> TicketContracts.validateIssue(read(node))).doesNotThrowAnyException();
    String hash = TicketContracts.hash(json, read(node));
    item.set("seats", json.createArrayNode().add(other).add(seat));
    assertThat(TicketContracts.hash(json, read(node))).isEqualTo(hash);
    item.set("seats", json.createArrayNode().add(seat).add(seat));
    assertThatThrownBy(() -> TicketContracts.validateIssue(read(node)))
        .isInstanceOf(InvalidContractException.class);
    item.set("seats", json.createArrayNode().add(seat));
    assertThatThrownBy(() -> TicketContracts.validateIssue(read(node)))
        .isInstanceOf(InvalidContractException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.5", "01", "-1", "+1", "1e2", "10000000000000000000"})
  void rejectsNoncanonicalOrOutOfRangeMoney(String price) {
    ObjectNode node = json.valueToTree(issue());
    ((ObjectNode) node.path("data").path("purchase").path("items").get(0)).put("unitPrice", price);
    assertThatThrownBy(() -> TicketContracts.validateIssue(read(node)))
        .isInstanceOf(InvalidContractException.class);
  }

  @Test
  void rejectsCountOverflowEvenWhenEachItemQuantityIsInt32() {
    var first = issue().data().purchase().items().getFirst();
    var huge =
        new OrderItem(
            first.orderItemId(),
            first.ticketTypeId(),
            first.ticketTypeName(),
            Integer.MAX_VALUE,
            "1",
            "2147483647",
            null,
            List.of());
    var more =
        new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "more", 1, "1", "1", null, List.of());
    var source = issue().data().purchase();
    var purchase =
        new PurchaseSnapshot(
            1,
            ORDER,
            EVENT,
            "buyer",
            "buyer@example.test",
            "2",
            "4",
            source.frozenAt(),
            source.expiresAt(),
            "VND",
            "Concert",
            source.eventStartsAt(),
            source.eventEndsAt(),
            null,
            "QUANTITY",
            null,
            "2147483648",
            "0",
            "2147483648",
            null,
            List.of(huge, more));
    assertThatThrownBy(
            () ->
                TicketContracts.validateIssue(
                    envelope(
                        "ticket.issue.v1", ORDER, "4", new IssueTickets(ORDER, CHARGE, purchase))))
        .isInstanceOf(InvalidContractException.class);
  }

  @Test
  void canonicalHashNormalizesUtcTimestampPrecision() {
    var plain = json.readTree("{\"occurredAt\":\"2026-10-30T12:00:00Z\"}");
    var padded = json.readTree("{\"occurredAt\":\"2026-10-30T12:00:00.000Z\"}");
    assertThat(TicketContracts.hash(json, plain)).isEqualTo(TicketContracts.hash(json, padded));
  }

  private Envelope<IssueTickets> read(ObjectNode node) {
    return TicketContracts.decode(json, json.writeValueAsBytes(node), IssueTickets.class);
  }

  private static Envelope<IssueTickets> issue() {
    var purchase =
        new PurchaseSnapshot(
            1,
            ORDER,
            EVENT,
            "buyer",
            "buyer@example.test",
            "2",
            "4",
            time("2026-10-30T12:00:00Z"),
            time("2026-10-30T12:01:00Z"),
            "VND",
            "Concert",
            time("2026-11-01T12:00:00Z"),
            time("2026-11-01T14:00:00Z"),
            null,
            "QUANTITY",
            null,
            "200",
            "0",
            "200",
            null,
            List.of(
                new OrderItem(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "General",
                    2,
                    "100",
                    "200",
                    null,
                    List.of())));
    return envelope("ticket.issue.v1", ORDER, "4", new IssueTickets(ORDER, CHARGE, purchase));
  }

  private static <T> Envelope<T> envelope(String type, UUID aggregate, String version, T data) {
    return new Envelope<>(
        UUID.randomUUID(),
        type,
        1,
        aggregate,
        version,
        time("2026-10-30T12:00:10Z"),
        type.equals("ticket.issue.v1") ? ORDER : EVENT,
        null,
        null,
        data);
  }

  private static Instant time(String value) {
    return Instant.parse(value);
  }
}
