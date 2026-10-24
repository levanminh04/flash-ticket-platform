package com.flashticket.booking_service.dto;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.dto.request.*;
import com.flashticket.booking_service.dto.snapshot.PromotionConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class BookingJsonTests {
  static JsonMapper mapper;

  @BeforeAll
  static void setup() {
    mapper = jsonMapper();
  }

  @Test
  void quantityRequestDeserializesWithTypedSelection() {
    var result =
        mapper.readValue(
            """
        {"eventId":"00000000-0000-0000-0000-000000000001","customerEmail":"buyer@example.com",
         "selection":{"mode":"QUANTITY","items":[{"ticketTypeId":"00000000-0000-0000-0000-000000000003","quantity":2}]}}
        """,
            CreateOrderRequest.class);
    assertThat(result.getEventId()).isEqualTo(EVENT);
    assertThat(result.getSelection()).isInstanceOf(OrderSelection.QuantitySelection.class);
    assertThat(
            ((OrderSelection.QuantitySelection) result.getSelection())
                .getItems()
                .getFirst()
                .getQuantity())
        .isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SEATED", "STANDING"})
  void seatMapDiscriminatorsSelectTheCorrectType(String kind) {
    String fields =
        kind.equals("SEATED")
            ? "\"seatIds\":[\"" + SEAT + "\"]"
            : "\"items\":[{\"ticketTypeId\":\"" + TICKET + "\",\"quantity\":1}]";
    var result =
        mapper.readValue(
            "{\"mode\":\"SEAT_MAP\",\"kind\":\""
                + kind
                + "\",\"sectorId\":\""
                + SECTOR
                + "\","
                + fields
                + "}",
            OrderSelection.class);
    assertThat(result)
        .isInstanceOf(
            kind.equals("SEATED")
                ? OrderSelection.SeatedSelection.class
                : OrderSelection.StandingSelection.class);
    var serialized = mapper.writeValueAsString(result);
    assertThat(mapper.readTree(serialized))
        .isEqualTo(
            mapper.readTree(
                "{\"mode\":\"SEAT_MAP\",\"kind\":\""
                    + kind
                    + "\",\"sectorId\":\""
                    + SECTOR
                    + "\","
                    + fields
                    + "}"));
    assertThat(mapper.readValue(serialized, OrderSelection.class))
        .usingRecursiveComparison()
        .isEqualTo(result);
    var request =
        mapper.readValue(
            "{\"eventId\":\""
                + EVENT
                + "\",\"customerEmail\":\"buyer@example.com\",\"selection\":"
                + serialized
                + "}",
            CreateOrderRequest.class);
    assertThat(request.getSelection()).usingRecursiveComparison().isEqualTo(result);
  }

  @Test
  void quantitySelectionRoundTripsWithoutAddingKind() {
    String json =
        "{\"mode\":\"QUANTITY\",\"items\":[{\"ticketTypeId\":\"" + TICKET + "\",\"quantity\":2}]}";
    var selection = mapper.readValue(json, OrderSelection.class);
    var serialized = mapper.writeValueAsString(selection);
    assertThat(mapper.readTree(serialized)).isEqualTo(mapper.readTree(json));
    assertThat(mapper.readValue(serialized, OrderSelection.class))
        .usingRecursiveComparison()
        .isEqualTo(selection);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "1",
        "{\"mode\":null}",
        "{\"mode\":1}",
        "{\"mode\":\"QUANTITY\",\"kind\":\"SEATED\",\"items\":[]}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":null}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":1}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":\"SEATED\",\"sectorId\":\"00000000-0000-0000-0000-000000000004\",\"items\":[]}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":\"STANDING\",\"sectorId\":\"00000000-0000-0000-0000-000000000004\",\"seatIds\":[]}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":\"SEATED\",\"seatIds\":[]}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":\"SEATED\",\"sectorId\":\"00000000-0000-0000-0000-000000000004\",\"seatIds\":[],\"extra\":true}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":\"STANDING\",\"sectorId\":\"00000000-0000-0000-0000-000000000004\",\"items\":[{\"ticketTypeId\":\"00000000-0000-0000-0000-000000000003\",\"quantity\":\"2\"}]}"
      })
  void rejectsInvalidSelectionDiscriminatorCombinationsAndFields(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, OrderSelection.class))
        .isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"mode\":\"UNKNOWN\"}",
        "{\"mode\":\"SEAT_MAP\"}",
        "{\"mode\":\"SEAT_MAP\",\"kind\":\"UNKNOWN\"}",
        "{\"mode\":\"QUANTITY\",\"items\":[],\"extra\":true}",
        "{\"mode\":\"QUANTITY\",\"items\":[{\"ticketTypeId\":\"bad\",\"quantity\":1}]}"
      })
  void rejectsMalformedSelectionJson(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, OrderSelection.class))
        .isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"provider\":\"OTHER\"}",
        "{\"provider\":0}",
        "{\"provider\":\"VNPAY\",\"extra\":true}"
      })
  void rejectsMissingUnknownOrNumericProvider(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, StartPaymentRequest.class))
        .isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "1.5", "true"})
  void textFieldsDoNotAcceptPrimitiveCoercion(String value) {
    assertThatThrownBy(
            () -> mapper.readValue("{\"code\":" + value + "}", ApplyPromotionRequest.class))
        .isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"2\"", "1.5"})
  void integerFieldsDoNotAcceptTextOrFraction(String value) {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"ticketTypeId\":\"" + TICKET + "\",\"quantity\":" + value + "}",
                    OrderSelection.QuantityItem.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void jsonRoundTripsVersionsMoneyAndTimeWithoutPrecisionLoss() {
    var value = with(purchase(), "sourceOrderVersion", "9223372036854775807");
    var tree = mapper.valueToTree(value);
    assertThat(tree.get("sourceOrderVersion").asString()).isEqualTo("9223372036854775807");
    assertThat(tree.get("subtotal").asString()).isEqualTo("200");
    assertThat(tree.has("validSnapshot")).isFalse();
    assertThat(mapper.treeToValue(tree, value.getClass())).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"FIXED", "PERCENT"})
  void promotionDiscriminatorRoundTrips(String kind) {
    String json =
        kind.equals("FIXED")
            ? "{\"kind\":\"FIXED\",\"amount\":\"10\"}"
            : "{\"kind\":\"PERCENT\",\"percent\":10}";
    var discount = mapper.readValue(json, PromotionConfig.Discount.class);
    assertThat(discount.kind()).isEqualTo(kind);
    assertThat(discount)
        .isInstanceOf(
            kind.equals("FIXED")
                ? PromotionConfig.FixedDiscount.class
                : PromotionConfig.PercentDiscount.class);
    assertThat(
            mapper.readValue(mapper.writeValueAsString(discount), PromotionConfig.Discount.class))
        .isEqualTo(discount);
    assertThat(mapper.writeValueAsString(discount)).containsOnlyOnce("\"kind\"");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"kind\":\"UNKNOWN\"}",
        "{\"kind\":null}",
        "{\"kind\":\"FIXED\",\"percent\":10}",
        "{\"kind\":\"PERCENT\",\"percent\":10,\"extra\":true}"
      })
  void rejectsInvalidPromotionDiscriminatorAndFields(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, PromotionConfig.Discount.class))
        .isInstanceOf(JacksonException.class);
  }
}
