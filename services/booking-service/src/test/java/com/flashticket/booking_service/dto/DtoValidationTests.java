package com.flashticket.booking_service.dto;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.dto.message.*;
import com.flashticket.booking_service.dto.request.*;
import com.flashticket.booking_service.dto.response.*;
import com.flashticket.booking_service.dto.snapshot.*;
import com.flashticket.booking_service.enums.*;
import jakarta.validation.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class DtoValidationTests {
  @org.junit.jupiter.api.Test
  void validatesBookingIssuanceResultBothOutcomes() {
    for (var state : BookingIssuanceResult.State.values()) {
      boolean completed = state == BookingIssuanceResult.State.COMPLETED;
      var valid =
          new BookingIssuanceResult(
              ORDER,
              SEAT,
              TICKET,
              state,
              completed ? 2 : 0,
              completed ? null : "ISSUANCE_REJECTED");
      assertThat(validator.validate(valid)).isEmpty();
      assertThat(
              validator.validate(
                  new BookingIssuanceResult(ORDER, SEAT, TICKET, state, null, valid.failureCode())))
          .isNotEmpty();
      assertThat(
              validator.validate(
                  new BookingIssuanceResult(ORDER, SEAT, TICKET, state, -1, valid.failureCode())))
          .isNotEmpty();
      assertThat(
              validator.validate(
                  new BookingIssuanceResult(
                      ORDER, SEAT, TICKET, state, completed ? 0 : 2, valid.failureCode())))
          .isNotEmpty();
      assertThat(
              validator.validate(
                  new BookingIssuanceResult(
                      ORDER, SEAT, TICKET, state, valid.ticketCount(), completed ? "ERROR" : null)))
          .isNotEmpty();
      assertThat(
              validator.validate(
                  new BookingIssuanceResult(
                      ORDER, SEAT, null, state, valid.ticketCount(), valid.failureCode())))
          .isNotEmpty();
    }
    assertThat(validator.validate(new BookingIssuanceResult(ORDER, SEAT, TICKET, null, 0, "ERROR")))
        .isNotEmpty();
    assertThat(
            validator.validate(
                new BookingIssuanceResult(
                    ORDER, SEAT, TICKET, BookingIssuanceResult.State.FAILED, 0, "  ")))
        .isNotEmpty();
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

  static Stream<Object> validContracts() {
    return Stream.concat(
        payloads().values().stream(),
        Stream.of(
            new CreateOrderRequest(
                EVENT,
                "buyer@example.com",
                new OrderSelection.QuantitySelection(
                    List.of(new OrderSelection.QuantityItem(TICKET, 2)))),
            new ApplyPromotionRequest("SAVE"),
            new FreezePaymentRequest("buyer"),
            new StartPaymentRequest(StartPaymentRequest.Provider.VNPAY),
            new OrderSelection.SeatedSelection(SECTOR, List.of(SEAT)),
            new OrderSelection.StandingSelection(
                SECTOR, List.of(new OrderSelection.QuantityItem(TICKET, 1))),
            new SelectionSnapshot(1, List.of(seat())),
            new PromotionConfigurationSnapshot(1, promotion()),
            promotion(),
            purchase(),
            sales(),
            new PromotionConfig.FixedDiscount("FIXED", "10"),
            new PromotionConfig.PercentDiscount("PERCENT", 1),
            new PromotionConfig.PercentDiscount("PERCENT", 100),
            new PaymentRequestAccepted(ORDER, MESSAGE, NOW),
            new Problem(
                "ERROR", "Error", EVENT, List.of(new Problem.Violation("selection", "INVALID"))),
            new OrderPage(List.of(), null),
            new Availability(
                EVENT,
                "1",
                NOW,
                List.of(new Availability.TicketTypeAvailability(TICKET, "0")),
                List.of(new Availability.SectorAvailability(SECTOR, SectorType.SEATED, "0")),
                List.of(new Availability.SeatAvailability(SEAT, SeatState.AVAILABLE)))));
  }

  @ParameterizedTest
  @MethodSource("validContracts")
  void acceptsValidContracts(Object dto) {
    assertThat(validator.validate(dto)).isEmpty();
  }

  static Stream<Object> invalidContracts() {
    var item = new OrderSelection.QuantityItem(TICKET, 1);
    return Stream.of(
        new CreateOrderRequest(
            null, "buyer@example.com", new OrderSelection.QuantitySelection(List.of(item))),
        new CreateOrderRequest(
            EVENT, "invalid", new OrderSelection.QuantitySelection(List.of(item))),
        new CreateOrderRequest(
            EVENT,
            "a".repeat(250) + "@example.com",
            new OrderSelection.QuantitySelection(List.of(item))),
        new CreateOrderRequest(EVENT, "buyer@example.com", null),
        new ApplyPromotionRequest(" "),
        new FreezePaymentRequest(""),
        new StartPaymentRequest(null),
        new OrderSelection.QuantityItem(TICKET, 0),
        new OrderSelection.QuantityItem(null, 1),
        new OrderSelection.QuantityItem(TICKET, -1),
        new OrderSelection.QuantitySelection(null),
        new OrderSelection.QuantitySelection(List.of()),
        new OrderSelection.QuantitySelection(List.of(item, item)),
        new OrderSelection.QuantitySelection(Arrays.asList(item, null)),
        new OrderSelection.SeatedSelection(null, List.of(SEAT)),
        new OrderSelection.SeatedSelection(SECTOR, List.of(SEAT, SEAT)),
        new OrderSelection.SeatedSelection(SECTOR, List.of()),
        new OrderSelection.SeatedSelection(SECTOR, null),
        new OrderSelection.StandingSelection(SECTOR, List.of(item, item)),
        new OrderSelection.StandingSelection(SECTOR, List.of()),
        new OrderSelection.StandingSelection(null, List.of(item)),
        new SelectionSnapshot(2, List.of()),
        new SelectionSnapshot(1, null),
        new PromotionConfigurationSnapshot(2, promotion()),
        new PromotionConfigurationSnapshot(1, null),
        with(promotion(), "maximumUsesPerUser", 2),
        with(promotion(), "maximumUses", "0"),
        with(promotion(), "endsAt", NOW),
        new PromotionConfig.PercentDiscount("PERCENT", 0),
        new PromotionConfig.PercentDiscount("PERCENT", 101),
        new PromotionConfig.FixedDiscount("FIXED", "0"),
        with(purchase(), "customerEmail", "invalid"),
        with(sales(), "purchaseLimit", 0),
        new PaymentRequestAccepted(null, MESSAGE, NOW),
        new Problem(" ", "Error", EVENT, List.of()),
        new Problem("ERROR", "Error", EVENT, List.of(new Problem.Violation("", "INVALID"))),
        new OrderPage(Arrays.asList((OrderView) null), null),
        new Availability(EVENT, "0", NOW, List.of(), List.of(), List.of()),
        new Availability.TicketTypeAvailability(TICKET, "-1"),
        new Availability.SectorAvailability(SECTOR, SectorType.SEATED, "9223372036854775808"),
        new Availability.SeatAvailability(null, SeatState.AVAILABLE),
        new OrderItemView(ORDER, TICKET, "Ticket", 0, "100", "0", null, List.of()));
  }

  @ParameterizedTest
  @MethodSource("invalidContracts")
  void rejectsInvalidContractsIncludingNestedValues(Object dto) {
    assertThat(validator.validate(dto)).as(dto.getClass().getSimpleName()).isNotEmpty();
  }

  @Test
  void allSupportedEnvelopesValidateAndValidateTheirPayload() {
    payloads()
        .forEach(
            (type, payload) -> assertThat(validator.validate(envelope(type, payload))).isEmpty());
    assertThat(
            validator.validate(
                envelope("booking.order-completed.v1", new OrderCompleted(null, SEAT, TICKET, 2))))
        .isNotEmpty();
    assertThat(validator.validate(envelope("unknown", sales()))).isNotEmpty();
    assertThat(validator.validate(envelope("booking.order-completed.v1", sales()))).isNotEmpty();
    var valid =
        envelope("booking.order-completed.v1", payloads().get("booking.order-completed.v1"));
    for (String field :
        List.of(
            "messageId",
            "messageType",
            "schemaVersion",
            "aggregateId",
            "aggregateVersion",
            "occurredAt",
            "correlationId",
            "data")) {
      assertThat(validator.validate(with(valid, field, null))).as(field).isNotEmpty();
    }
    assertThat(validator.validate(with(valid, "schemaVersion", 2))).isNotEmpty();
    assertThat(validator.validate(with(valid, "aggregateVersion", "9223372036854775808")))
        .isNotEmpty();
    var start =
        envelope("payment.start-requested.v1", payloads().get("payment.start-requested.v1"));
    assertThat(validator.validate(with(start, "messageId", SEAT))).isNotEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "ACCEPTED,same,true",
    "ACCEPTED,other,false",
    "ACCEPTED,null,false",
    "DUPLICATE,other,true",
    "DUPLICATE,same,false",
    "DUPLICATE,null,false",
    "LATE,null,true",
    "LATE,same,false",
    "CANCELLED,null,true",
    "CANCELLED,other,false"
  })
  void paymentDecisionConsistency(PaymentDecision.Outcome outcome, String accepted, boolean valid) {
    UUID charge = "same".equals(accepted) ? SEAT : "other".equals(accepted) ? TICKET : null;
    assertThat(validator.validate(new PaymentDecision(ORDER, SEAT, NOW, outcome, charge)).isEmpty())
        .isEqualTo(valid);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "COMPLETED|1|NULL|true",
        "COMPLETED|0|NULL|false",
        "COMPLETED|1|ERROR|false",
        "FAILED|0|ERROR|true",
        "FAILED|1|ERROR|false",
        "FAILED|0|NULL|false",
        "FAILED|0| |false"
      },
      delimiter = '|',
      nullValues = "NULL")
  void issuanceResultConsistency(
      IssuanceResult.State state, int count, String failure, boolean valid) {
    assertThat(
            validator
                .validate(new IssuanceResult(ORDER, SEAT, TICKET, state, count, failure))
                .isEmpty())
        .isEqualTo(valid);
  }

  @Test
  void missingResultAndDecisionStateAreRejected() {
    assertThat(validator.validate(new PaymentDecision(ORDER, SEAT, NOW, null, null))).isNotEmpty();
    assertThat(validator.validate(new IssuanceResult(ORDER, SEAT, TICKET, null, null, null)))
        .isNotEmpty();
    assertThat(validator.validate(new BookingIssuanceResult(ORDER, SEAT, TICKET, null, null, "")))
        .isNotEmpty();
  }

  @Test
  void nestedPromotionDiscountMustBeValidated() {
    var invalid =
        with(promotion(), "discount", new PromotionConfig.PercentDiscount("PERCENT", 101));
    assertThat(validator.validate(invalid)).isNotEmpty();
  }

  static Stream<Arguments> invalidNestedPayloads() {
    var start = (StartPayment) payloads().get("payment.start-requested.v1");
    var cancellation = (EventCancellation) payloads().get("event.cancelled.v1");
    return Stream.of(
        Arguments.of(
            envelope(
                "payment.start-requested.v1",
                with(start, "purchase", with(purchase(), "customerEmail", "invalid"))),
            "data.purchase.customerEmail"),
        Arguments.of(
            envelope(
                "event.cancelled.v1",
                with(cancellation, "sales", with(sales(), "purchaseLimit", 0))),
            "data.sales.purchaseLimit"),
        Arguments.of(
            envelope(
                "event.cancelled.v1",
                with(cancellation, "access", with(cancellation.access(), "organizerSubject", ""))),
            "data.access.organizerSubject"));
  }

  @ParameterizedTest
  @MethodSource("invalidNestedPayloads")
  void envelopeValidationReachesNestedPayloads(Envelope<?> value, String expectedPath) {
    assertThat(validator.validate(value))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains(expectedPath);
  }
}
