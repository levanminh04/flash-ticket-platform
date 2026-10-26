package com.flashticket.ticket_service.dto;

import static com.flashticket.ticket_service.support.TicketFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.dto.message.*;
import com.flashticket.ticket_service.dto.snapshot.*;
import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class TicketContractTests {
  @Test
  void customerEmailCannotExceed254Characters() {
    ObjectNode tree = jsonMapper().valueToTree(purchase());
    String email =
        "a".repeat(64)
            + "@"
            + "b".repeat(63)
            + "."
            + "c".repeat(63)
            + "."
            + "d".repeat(60)
            + ".test";
    assertThat(email.length()).isGreaterThan(254);
    tree.put("customerEmail", email);
    assertThat(validator.validate(jsonMapper().treeToValue(tree, PurchaseSnapshot.class)))
        .isNotEmpty();
  }

  @Test
  void promotionSubtypeKindMustMatchItsWireShape() {
    assertThat(validator.validate(new PromotionConfig.FixedDiscount("PERCENT", "10"))).isNotEmpty();
    assertThat(validator.validate(new PromotionConfig.PercentDiscount("FIXED", 10))).isNotEmpty();
  }

  static ValidatorFactory factory;
  static Validator validator;

  @BeforeAll
  static void setup() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void acceptsEarlyCancellationAndRejectsMissingPayload() {
    for (Object value :
        List.of(
            purchase(),
            sales(),
            access(),
            issue(),
            completed(),
            failed(),
            resultEnvelope(),
            cancellation())) assertThat(validator.validate(value)).isEmpty();
    assertThat(validator.validate(new IssueTickets(ORDER, CHARGE, null))).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "9223372036854775808", "01", "-1"})
  void rejectsInvalidVersion(String version) {
    ObjectNode tree = jsonMapper().valueToTree(purchase());
    tree.put("sourceOrderVersion", version);
    assertThat(validator.validate(jsonMapper().treeToValue(tree, PurchaseSnapshot.class)))
        .isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"201", "1.01", "1e3", "-1"})
  void rejectsInvalidTotalsAndSelections(String total) {
    ObjectNode tree = jsonMapper().valueToTree(purchase());
    tree.put("total", total);
    assertThat(validator.validate(jsonMapper().treeToValue(tree, PurchaseSnapshot.class)))
        .isNotEmpty();
  }

  @Test
  void rejectsDuplicateItemAndSeatIds() {
    var p = purchase();
    var item = p.items().getFirst();
    ObjectNode tree = jsonMapper().valueToTree(p);
    tree.withArray("items").add(jsonMapper().valueToTree(item));
    tree.put("subtotal", "400");
    tree.put("total", "400");
    assertThat(validator.validate(jsonMapper().treeToValue(tree, PurchaseSnapshot.class)))
        .isNotEmpty();
    tree = jsonMapper().valueToTree(p);
    tree.put("mode", "SEAT_MAP");
    tree.put("sectorId", SECTOR.toString());
    ObjectNode child = (ObjectNode) tree.withArray("items").get(0);
    child.put("sectorId", SECTOR.toString());
    var seat = new SeatSelectionSnapshot(SEAT, "A1", "A", "1");
    child.withArray("seats").add(jsonMapper().valueToTree(seat));
    assertThat(validator.validate(jsonMapper().treeToValue(tree, PurchaseSnapshot.class)))
        .isNotEmpty();
    child.withArray("seats").add(jsonMapper().valueToTree(seat));
    assertThat(validator.validate(jsonMapper().treeToValue(tree, PurchaseSnapshot.class)))
        .isNotEmpty();
  }

  @Test
  void standingSalesCannotContainSeats() {
    ObjectNode tree = jsonMapper().valueToTree(sales());
    tree.put("mode", "SEAT_MAP");
    ((ObjectNode) tree.withArray("ticketTypes").get(0)).put("sectorId", SECTOR.toString());
    tree.withArray("sectors")
        .addObject()
        .put("sectorId", SECTOR.toString())
        .put("kind", "STANDING")
        .put("configuredCapacity", "20");
    tree.withArray("seats")
        .addObject()
        .put("seatId", SEAT.toString())
        .put("sectorId", SECTOR.toString())
        .put("ticketTypeId", TYPE.toString())
        .put("seatLabel", "A1")
        .put("rowName", "A")
        .put("seatNumber", "1")
        .put("isHidden", false);
    assertThat(validator.validate(jsonMapper().treeToValue(tree, SalesConfiguration.class)))
        .isNotEmpty();
  }

  @Test
  void cancellationIdsVersionsAndStatusMustAgree() {
    for (String field : List.of("eventId", "sourceConfigVersion", "eventStatus")) {
      ObjectNode tree = jsonMapper().valueToTree(cancellation());
      ((ObjectNode) tree.get("access"))
          .put(
              field,
              switch (field) {
                case "eventId" -> ORDER.toString();
                case "sourceConfigVersion" -> "1";
                default -> "PUBLISHED";
              });
      assertThat(validator.validate(jsonMapper().treeToValue(tree, EventCancellation.class)))
          .isNotEmpty();
    }
    ObjectNode missing = jsonMapper().valueToTree(cancellation());
    missing.set("sales", jsonMapper().nullNode());
    assertThat(validator.validate(jsonMapper().treeToValue(missing, EventCancellation.class)))
        .isNotEmpty();
  }

  @Test
  void immutableListsKeepCallerChangesOut() {
    var input = new ArrayList<PurchaseSnapshot.Item>();
    input.add(purchase().items().getFirst());
    var p = purchase();
    var value =
        new PurchaseSnapshot(
            p.schemaVersion(),
            p.orderId(),
            p.eventId(),
            p.buyerSubject(),
            p.customerEmail(),
            p.sourceConfigVersion(),
            p.sourceOrderVersion(),
            p.frozenAt(),
            p.expiresAt(),
            p.currency(),
            p.eventTitle(),
            p.eventStartsAt(),
            p.eventEndsAt(),
            p.venueName(),
            p.mode(),
            p.sectorId(),
            p.subtotal(),
            p.discount(),
            p.total(),
            p.promotion(),
            input);
    input.clear();
    assertThat(value.items()).hasSize(1);
    input.add(null);
    var nullValue =
        new PurchaseSnapshot(
            p.schemaVersion(),
            p.orderId(),
            p.eventId(),
            p.buyerSubject(),
            p.customerEmail(),
            p.sourceConfigVersion(),
            p.sourceOrderVersion(),
            p.frozenAt(),
            p.expiresAt(),
            p.currency(),
            p.eventTitle(),
            p.eventStartsAt(),
            p.eventEndsAt(),
            p.venueName(),
            p.mode(),
            p.sectorId(),
            p.subtotal(),
            p.discount(),
            p.total(),
            p.promotion(),
            input);
    assertThat(validator.validate(nullValue)).isNotEmpty();
  }

  @Test
  void issueAndEnvelopeIdsAreCheckedByPayloadType() {
    assertThat(validator.validate(new IssueTickets(EVENT, CHARGE, purchase()))).isNotEmpty();
    var original = resultEnvelope();
    assertThat(
            validator.validate(
                new Envelope<>(
                    MESSAGE,
                    original.messageType(),
                    1,
                    EVENT,
                    "1",
                    NOW,
                    CORRELATION,
                    null,
                    null,
                    completed())))
        .isNotEmpty();
    assertThat(
            validator.validate(
                new Envelope<>(
                    MESSAGE,
                    "event.cancelled.v1",
                    1,
                    EVENT,
                    "2",
                    NOW,
                    CORRELATION,
                    null,
                    null,
                    cancellation())))
        .isEmpty();
  }

  @Test
  void terminalResultHasNoPendingOrPartialSuccess() {
    assertThat(
            validator.validate(
                new IssuanceResult(
                    ORDER, CHARGE, ISSUANCE, IssuanceResult.State.FAILED, 1, "FAILED")))
        .isNotEmpty();
    assertThat(
            validator.validate(
                new IssuanceResult(
                    ORDER, CHARGE, ISSUANCE, IssuanceResult.State.COMPLETED, 0, null)))
        .isNotEmpty();
  }
}
