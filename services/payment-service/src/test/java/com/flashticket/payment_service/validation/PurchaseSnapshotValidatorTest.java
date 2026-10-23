package com.flashticket.payment_service.validation;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class PurchaseSnapshotValidatorTest {
  static final PaymentContractFixtures F = new PaymentContractFixtures();
  final PurchaseSnapshotValidator validator =
      new PurchaseSnapshotValidator(F.factory.getValidator());

  @AfterAll
  static void close() {
    F.close();
  }

  @Test
  void acceptsQuantitySeatedAndStandingWithExactCount() throws Exception {
    for (var tree : new ObjectNode[] {F.quantity(), F.seatMap(true), F.seatMap(false)}) {
      assertThat(validator.expectedTicketCount(F.purchase(tree))).isEqualTo(3);
    }
    var maximum =
        F.codec.read(PaymentContractFixtures.resource("purchase.json"), PurchaseSnapshot.class);
    assertThat(validator.expectedTicketCount(maximum)).isEqualTo(1);
  }

  record Bad(String field, String code, Consumer<ObjectNode> change) {}

  @Test
  void rejectsInconsistentArithmeticIdentityAndTime() throws Exception {
    var cases =
        new Bad[] {
          new Bad(
              "$.items[0].lineSubtotal",
              "LINE_TOTAL_MISMATCH",
              n -> line(n, 0).put("lineSubtotal", "199")),
          new Bad("$.subtotal", "SUBTOTAL_MISMATCH", n -> n.put("subtotal", "251")),
          new Bad("$.total", "TOTAL_MISMATCH", n -> n.put("total", "224")),
          new Bad("$.discount", "DISCOUNT_MISMATCH", n -> n.put("discount", "251")),
          new Bad("$.discount", "DISCOUNT_MISMATCH", n -> n.putNull("promotion")),
          new Bad(
              "$.promotion.discount",
              "DISCOUNT_MISMATCH",
              n -> ((ObjectNode) n.get("promotion")).put("discount", "24")),
          new Bad(
              "$.items[1].orderItemId",
              "DUPLICATE_ID",
              n -> line(n, 1).set("orderItemId", line(n, 0).get("orderItemId"))),
          new Bad("$.frozenAt", "INVALID_TIME_RANGE", n -> n.set("frozenAt", n.get("expiresAt"))),
          new Bad(
              "$.eventStartsAt",
              "INVALID_TIME_RANGE",
              n -> n.set("eventStartsAt", n.get("eventEndsAt"))),
          new Bad(
              "$.sectorId",
              "INVALID_SELECTION",
              n -> n.put("sectorId", "10000000-0000-0000-0000-000000000007"))
        };
    for (var bad : cases) {
      var tree = F.quantity();
      bad.change().accept(tree);
      fails(tree, bad.field(), bad.code());
    }
  }

  @Test
  void rejectsInvalidSeatSelection() throws Exception {
    var node = F.seatMap(true);
    line(node, 1).withArray("seats").set(0, line(node, 0).withArray("seats").get(0));
    fails(node, "$.items[1].seats[0].seatId", "DUPLICATE_ID");
    node = F.seatMap(true);
    line(node, 0).withArray("seats").remove(0);
    fails(node, "$.items[0].seats", "INVALID_SELECTION");
    node = F.seatMap(true);
    line(node, 1).withArray("seats").removeAll();
    fails(node, "$.items[1].seats", "INVALID_SELECTION");
    node = F.seatMap(false);
    node.putNull("sectorId");
    fails(node, "$.sectorId", "INVALID_SELECTION");
    node = F.seatMap(false);
    line(node, 1).putNull("sectorId");
    fails(node, "$.items[1].sectorId", "INVALID_SELECTION");
    node = F.quantity();
    line(node, 0).set("seats", line(F.seatMap(true), 0).get("seats"));
    fails(node, "$.items[0].seats", "INVALID_SELECTION");
    node = F.quantity();
    line(node, 0).put("sectorId", "10000000-0000-0000-0000-000000000007");
    fails(node, "$.items[0].sectorId", "INVALID_SELECTION");
  }

  @Test
  void rejectsOverflowBeforeNarrowing() throws Exception {
    var node = F.quantity();
    line(node, 0).put("unitPrice", "9999999999999999999");
    fails(node, "$.items[0].lineSubtotal", "AMOUNT_OUT_OF_RANGE");
    node = F.quantity();
    for (var item : node.withArray("items")) {
      ((ObjectNode) item)
          .put("quantity", 1)
          .put("unitPrice", "9999999999999999999")
          .put("lineSubtotal", "9999999999999999999");
    }
    fails(node, "$.subtotal", "AMOUNT_OUT_OF_RANGE");
    node = F.quantity();
    line(node, 0)
        .put("quantity", Integer.MAX_VALUE)
        .put("unitPrice", "1")
        .put("lineSubtotal", "2147483647");
    line(node, 1).put("unitPrice", "1").put("lineSubtotal", "1");
    node.put("subtotal", "2147483648").put("total", "2147483623");
    fails(node, "$.items", "TICKET_COUNT_OUT_OF_RANGE");
  }

  @Test
  void checksDirectObjectsAndKeepsOldSnapshotsReadable() throws Exception {
    assertThatThrownBy(() -> validator.validate(null))
        .isInstanceOf(ContractValidationException.class);
    var node = F.quantity();
    node.put("total", "0");
    // Direct mapping bypasses codec Bean Validation; validator must still reject it.
    var invalid = F.mapper.treeToValue(node, PurchaseSnapshot.class);
    assertThatThrownBy(() -> validator.validate(invalid))
        .isInstanceOf(ContractValidationException.class);
    node = F.quantity();
    for (var key : new String[] {"frozenAt", "expiresAt", "eventStartsAt", "eventEndsAt"})
      node.put(key, node.get(key).asString().replace("2030", "2000"));
    var oldSnapshot = F.purchase(node);
    assertThatCode(() -> validator.validate(oldSnapshot)).doesNotThrowAnyException();
  }

  static ObjectNode nodeCopy(ObjectNode n) {
    return n.deepCopy();
  }

  static ObjectNode line(ObjectNode n, int i) {
    return (ObjectNode) n.withArray("items").get(i);
  }

  void fails(ObjectNode tree, String field, String code) {
    var ex = catchThrowable(() -> validator.validate(F.purchase(tree)));
    assertThat(ex).isInstanceOf(ContractValidationException.class);
    assertThat(((ContractValidationException) ex).field()).isEqualTo(field);
    assertThat(((ContractValidationException) ex).code()).isEqualTo(code);
  }
}
