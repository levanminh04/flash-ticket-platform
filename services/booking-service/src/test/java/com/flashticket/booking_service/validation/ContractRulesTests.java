package com.flashticket.booking_service.validation;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.dto.snapshot.*;
import com.flashticket.booking_service.enums.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ContractRulesTests {
  @ParameterizedTest
  @CsvSource({
    "1,true",
    "9223372036854775807,true",
    "9223372036854775808,false",
    "9999999999999999999,false",
    "0,false",
    "-1,false",
    "01,false",
    "1.0,false",
    "1e3,false",
    "'',false",
    "' 1',false"
  })
  void versionBounds(String value, boolean expected) {
    assertThat(ContractRules.validUInt64(value)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "0,true",
    "1,true",
    "9999999999999999999,true",
    "10000000000000000000,false",
    "-1,false",
    "01,false",
    "1.0,false",
    "1e3,false",
    "'',false",
    "' 1',false"
  })
  void moneyWireFormat(String value, boolean expected) {
    assertThat(ContractRules.validMoney(value)).isEqualTo(expected);
  }

  @Test
  void nullScalarsAreInvalid() {
    assertThat(ContractRules.validUInt64(null)).isFalse();
    assertThat(ContractRules.validMoney(null)).isFalse();
    assertThat(ContractRules.validWindow(null, NOW)).isFalse();
    assertThat(ContractRules.validWindow(NOW, null)).isFalse();
    assertThat(ContractRules.validTotals(null, "0", "1")).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "200,20,180,true",
    "200,0,200,true",
    "200,200,0,false",
    "200,201,1,false",
    "200,20,179,false",
    "-1,0,1,false",
    "1.5,0,1,false",
    "9999999999999999999,1,9999999999999999998,true"
  })
  void totalsUseExactIntegerArithmetic(
      String subtotal, String discount, String total, boolean valid) {
    assertThat(ContractRules.validTotals(subtotal, discount, total)).isEqualTo(valid);
  }

  @Test
  void timeWindowIsStrict() {
    assertThat(ContractRules.validWindow(NOW, NOW.plusSeconds(1))).isTrue();
    assertThat(ContractRules.validWindow(NOW, NOW)).isFalse();
    assertThat(ContractRules.validWindow(NOW, NOW.minusSeconds(1))).isFalse();
  }

  @Test
  void validSnapshotsAreAccepted() {
    assertThat(ContractRules.validSalesConfiguration(sales())).isTrue();
    assertThat(ContractRules.validPurchaseSnapshot(purchase())).isTrue();
    assertThat(ContractRules.validSalesConfiguration(null)).isFalse();
    assertThat(ContractRules.validPurchaseSnapshot(null)).isFalse();
  }

  static Stream<Arguments> badSales() {
    var sales = sales();
    var ticket = sales.ticketTypes().getFirst();
    var sector = new SalesConfiguration.SectorConfig(SECTOR, SectorType.SEATED, "10");
    var seat = new SalesConfiguration.SeatConfig(SEAT, SECTOR, TICKET, "A1", "A", "1", false);
    return Stream.of(
        Arguments.of("schemaVersion", 2),
        Arguments.of("sourceConfigVersion", "0"),
        Arguments.of("saleEndsAt", NOW),
        Arguments.of("eventEndsAt", sales.eventStartsAt()),
        Arguments.of("ticketTypes", null),
        Arguments.of("sectors", null),
        Arguments.of("seats", null),
        Arguments.of("promotions", null),
        Arguments.of("ticketTypes", Arrays.asList(ticket, null)),
        Arguments.of("ticketTypes", List.of(ticket, ticket)),
        Arguments.of("ticketTypes", List.of(with(ticket, "sectorId", SECTOR))),
        Arguments.of("sectors", List.of(sector)),
        Arguments.of("seats", List.of(seat)),
        Arguments.of("promotions", List.of(promotion(), promotion())),
        Arguments.of("promotions", List.of(with(promotion(), "endsAt", NOW))));
  }

  @ParameterizedTest
  @MethodSource("badSales")
  void rejectsInvalidSalesSnapshot(String field, Object invalid) {
    assertThat(ContractRules.validSalesConfiguration(with(sales(), field, invalid))).isFalse();
  }

  @Test
  void seatMapReferencesMustResolveAndBeUnique() {
    var sector = new SalesConfiguration.SectorConfig(SECTOR, SectorType.SEATED, "10");
    var seat = new SalesConfiguration.SeatConfig(SEAT, SECTOR, TICKET, "A1", "A", "1", false);
    var valid =
        with(
            with(with(sales(), "mode", SalesMode.SEAT_MAP), "sectors", List.of(sector)),
            "seats",
            List.of(seat));
    valid =
        with(
            valid,
            "ticketTypes",
            List.of(with(sales().ticketTypes().getFirst(), "sectorId", SECTOR)));
    assertThat(ContractRules.validSalesConfiguration(valid)).isTrue();
    assertThat(ContractRules.validSalesConfiguration(with(valid, "sectors", List.of()))).isFalse();
    assertThat(ContractRules.validSalesConfiguration(with(valid, "ticketTypes", List.of())))
        .isFalse();
    assertThat(
            ContractRules.validSalesConfiguration(with(valid, "sectors", List.of(sector, sector))))
        .isFalse();
    assertThat(ContractRules.validSalesConfiguration(with(valid, "seats", List.of(seat, seat))))
        .isFalse();
    assertThat(
            ContractRules.validSalesConfiguration(
                with(valid, "sectors", List.of(with(sector, "kind", SectorType.STANDING)))))
        .isFalse();
    assertThat(
            ContractRules.validSalesConfiguration(
                with(
                    sales(),
                    "ticketTypes",
                    List.of(with(sales().ticketTypes().getFirst(), "configuredQuantity", null)))))
        .isFalse();
  }

  static Stream<Arguments> badPurchase() {
    var purchase = purchase();
    var item = purchase.items().getFirst();
    return Stream.of(
        Arguments.of("schemaVersion", 2),
        Arguments.of("sourceConfigVersion", "0"),
        Arguments.of("sourceOrderVersion", "9223372036854775808"),
        Arguments.of("frozenAt", null),
        Arguments.of("expiresAt", NOW),
        Arguments.of("eventEndsAt", purchase.eventStartsAt()),
        Arguments.of("items", null),
        Arguments.of("items", Arrays.asList(item, null)),
        Arguments.of("items", List.of(item, item)),
        Arguments.of("sectorId", SECTOR),
        Arguments.of("discount", "1"),
        Arguments.of("subtotal", "201"),
        Arguments.of("items", List.of(with(item, "lineSubtotal", "100"))),
        Arguments.of("items", List.of(with(item, "unitPrice", "-1"))),
        Arguments.of("items", List.of(with(item, "seats", null))),
        Arguments.of("items", List.of(with(item, "seats", List.of(seat())))));
  }

  @ParameterizedTest
  @MethodSource("badPurchase")
  void rejectsInvalidPurchaseSnapshot(String field, Object invalid) {
    assertThat(ContractRules.validPurchaseSnapshot(with(purchase(), field, invalid))).isFalse();
  }

  @Test
  void rejectsDuplicateSeatsAcrossItems() {
    var item = with(purchase().items().getFirst(), "seats", List.of(seat(), seat()));
    assertThat(ContractRules.validPurchaseSnapshot(with(purchase(), "items", List.of(item))))
        .isFalse();
    var first =
        new PurchaseSnapshot.Item(
            MESSAGE, TICKET, "Standard", 1, "100", "100", SECTOR, List.of(seat()));
    var second = with(first, "orderItemId", SEAT);
    var seated =
        with(
            with(with(purchase(), "mode", SalesMode.SEAT_MAP), "sectorId", SECTOR),
            "items",
            List.of(first, second));
    assertThat(ContractRules.validPurchaseSnapshot(seated)).isFalse();
  }

  @Test
  void promotionMustMatchSnapshotDiscount() {
    var discounted =
        with(
            with(with(purchase(), "discount", "20"), "total", "180"),
            "promotion",
            new PurchaseSnapshot.AppliedPromotionSnapshot(MESSAGE, "SAVE", "20"));
    assertThat(ContractRules.validPurchaseSnapshot(discounted)).isTrue();
    assertThat(
            ContractRules.validPurchaseSnapshot(
                with(
                    discounted,
                    "promotion",
                    new PurchaseSnapshot.AppliedPromotionSnapshot(MESSAGE, "SAVE", "19"))))
        .isFalse();
  }

  @Test
  void payloadRegistryRejectsUnknownAndMismatchedTypes() {
    payloads()
        .forEach(
            (name, payload) -> {
              assertThat(ContractRules.expectedPayloadType(name)).isEqualTo(payload.getClass());
              assertThat(ContractRules.isPayloadTypeValid(name, payload)).isTrue();
              assertThat(ContractRules.matchesPayload(name, payload.getClass())).isTrue();
              assertThat(ContractRules.isPayloadTypeValid(name, new Object())).isFalse();
            });
    assertThat(ContractRules.isPayloadTypeValid(null, sales())).isFalse();
    assertThat(ContractRules.isPayloadTypeValid("unknown", sales())).isFalse();
    assertThat(ContractRules.isPayloadTypeValid("event.sales-configured.v1", null)).isFalse();
    assertThatThrownBy(() -> ContractRules.expectedPayloadType("unknown"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
