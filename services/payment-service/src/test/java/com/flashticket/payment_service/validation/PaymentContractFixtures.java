package com.flashticket.payment_service.validation;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class PaymentContractFixtures implements AutoCloseable {
  public final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
  public final JsonMapper mapper = JsonMapper.builder().build();
  public final ContractCodec codec = new ContractCodec(mapper, factory.getValidator());

  public static String resource(String name) throws Exception {
    try (var stream = PaymentContractFixtures.class.getResourceAsStream("/contracts/" + name)) {
      if (stream == null) throw new IllegalArgumentException("Missing fixture: " + name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  public ObjectNode quantity() throws Exception {
    var node = (ObjectNode) mapper.readTree(resource("purchase.json"));
    var items = node.withArray("items");
    var first = (ObjectNode) items.get(0);
    first.put("quantity", 2).put("unitPrice", "100").put("lineSubtotal", "200");
    var second = first.deepCopy();
    second.put("orderItemId", "10000000-0000-0000-0000-000000000009");
    second.put("quantity", 1).put("unitPrice", "50").put("lineSubtotal", "50");
    items.add(second);
    node.put("subtotal", "250").put("discount", "25").put("total", "225");
    var promotion = mapper.createObjectNode();
    promotion.put("promotionId", "10000000-0000-0000-0000-000000000008");
    promotion.put("code", "SAVE").put("discount", "25");
    node.set("promotion", promotion);
    return node;
  }

  public ObjectNode seatMap(boolean seated) throws Exception {
    var node = quantity();
    String sector = "10000000-0000-0000-0000-000000000007";
    node.put("mode", "SEAT_MAP").put("sectorId", sector);
    int id = 20;
    for (var item : node.withArray("items")) {
      var line = (ObjectNode) item;
      line.put("sectorId", sector);
      if (seated) {
        for (int i = 0; i < line.get("quantity").asInt(); i++) {
          var seat = mapper.createObjectNode();
          seat.put("seatId", "10000000-0000-0000-0000-%012d".formatted(id++));
          seat.put("seatLabel", "A" + id).put("rowName", "A").put("seatNumber", "" + id);
          line.withArray("seats").add(seat);
        }
      }
    }
    return node;
  }

  public PurchaseSnapshot purchase(ObjectNode tree) {
    return codec.fromTree(tree, PurchaseSnapshot.class);
  }

  @Override
  public void close() {
    factory.close();
  }
}
