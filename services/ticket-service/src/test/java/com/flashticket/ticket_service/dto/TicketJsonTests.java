package com.flashticket.ticket_service.dto;

import static com.flashticket.ticket_service.support.TicketFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.dto.message.*;
import com.flashticket.ticket_service.dto.request.CheckInRequest;
import com.flashticket.ticket_service.dto.response.*;
import com.flashticket.ticket_service.dto.snapshot.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class TicketJsonTests {
  static JsonMapper json;

  @BeforeAll
  static void setup() {
    json = jsonMapper();
  }

  @Test
  void preservesWireTypesAndRequiredNullableFields() {
    var value = purchase();
    var tree = json.valueToTree(value);
    assertThat(tree.get("subtotal").isString()).isTrue();
    assertThat(tree.get("subtotal").asString()).isEqualTo("200");
    assertThat(tree.get("venueName").isNull()).isTrue();
    assertThat(tree.has("validSnapshot")).isFalse();
    assertThat(json.treeToValue(tree, PurchaseSnapshot.class)).isEqualTo(value);
    ObjectNode missing = json.valueToTree(value);
    missing.remove("venueName");
    assertThatThrownBy(() -> json.treeToValue(missing, PurchaseSnapshot.class))
        .isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"{\"qrToken\":123}", "{\"qrToken\":true}", "{\"qrToken\":\"x\",\"extra\":true}"})
  void rejectsUnknownFieldsAndPrimitiveCoercion(String input) {
    assertThatThrownBy(() -> json.readValue(input, CheckInRequest.class))
        .isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.5", "\"2\""})
  void rejectsFractionalOrTextQuantity(String quantity) {
    var input =
        json.writeValueAsString(purchase()).replace("\"quantity\":2", "\"quantity\":" + quantity);
    assertThatThrownBy(() -> json.readValue(input, PurchaseSnapshot.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void versionsKeepBigintPrecision() {
    ObjectNode tree = json.valueToTree(purchase());
    tree.put("sourceOrderVersion", "9223372036854775807");
    var value = json.treeToValue(tree, PurchaseSnapshot.class);
    assertThat(value.sourceOrderVersion()).isEqualTo("9223372036854775807");
    tools.jackson.databind.JsonNode serialized = json.valueToTree(value);
    assertThat(serialized).isEqualTo(tree);
  }

  @Test
  void probeHasOnlyItsContractFields() {
    var pending = new PendingIssuanceView("PENDING", ORDER, ISSUANCE);
    var pendingTree = json.valueToTree(pending);
    assertThat(pendingTree.size()).isEqualTo(3);
    assertThat(pendingTree.get("state").asString()).isEqualTo("PENDING");
    assertThat(json.treeToValue(pendingTree, IssuanceProbeView.class)).isEqualTo(pending);
    var terminalTree = json.valueToTree(completed());
    assertThat(terminalTree.size()).isEqualTo(6);
    assertThat(json.treeToValue(terminalTree, IssuanceProbeView.class)).isEqualTo(completed());
    assertThat(json.treeToValue(json.valueToTree(failed()), IssuanceProbeView.class))
        .isEqualTo(failed());
  }

  @Test
  void optionalFieldCanBeAbsent() {
    ObjectNode tree = json.valueToTree(resultEnvelope());
    tree.remove(List.of("causationId", "traceparent"));
    Envelope<IssuanceResult> value = json.readValue(tree.toString(), new TypeReference<>() {});
    assertThat(value.causationId()).isNull();
    assertThat(value.traceparent()).isNull();
    assertThat(value.data()).isEqualTo(completed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"FIXED", "PERCENT"})
  void promotionDiscriminatorRoundTrips(String kind) {
    String input =
        kind.equals("FIXED")
            ? "{\"kind\":\"FIXED\",\"amount\":\"10\"}"
            : "{\"kind\":\"PERCENT\",\"percent\":10}";
    var discount = json.readValue(input, PromotionConfig.Discount.class);
    assertThat(json.readTree(json.writeValueAsString(discount))).isEqualTo(json.readTree(input));
  }
}
