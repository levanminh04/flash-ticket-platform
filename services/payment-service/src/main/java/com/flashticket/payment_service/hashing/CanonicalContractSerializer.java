package com.flashticket.payment_service.hashing;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.ContractViolations;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.IssueTickets;
import com.flashticket.payment_service.dto.message.PaymentProvider;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Validator;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Canonical v1 encoding; callers validate semantic invariants before encoding. */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CanonicalContractSerializer {
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE).build();
  Validator validator;

  public CanonicalContractSerializer(Validator validator) {
    this.validator = java.util.Objects.requireNonNull(validator);
  }

  public byte[] purchaseBytes(PurchaseSnapshot purchase) {
    var tree = validatedTree(purchase);
    sortPurchase(tree);
    return bytes(tree);
  }

  public byte[] requestBytes(UUID orderId, PaymentProvider provider, String actor) {
    if (orderId == null || provider == null || actor == null || actor.isBlank())
      throw new ContractValidationException("$", "CONSTRAINT_VIOLATION");
    var tree = JSON.createObjectNode();
    tree.put("actor", actor).put("orderId", orderId.toString()).put("provider", provider.name());
    return bytes(tree);
  }

  public byte[] envelopeBytes(Envelope<?> envelope) {
    var tree = validatedTree(envelope);
    if (envelope.data() instanceof StartPayment || envelope.data() instanceof IssueTickets)
      sortPurchase(tree.get("data").get("purchase"));
    return bytes(tree);
  }

  private JsonNode validatedTree(Object value) {
    if (value == null) throw new ContractValidationException("$", "REQUIRED");
    ContractViolations.firstPath(validator.validate(value))
        .ifPresent(
            path -> {
              throw new ContractValidationException(path, "CONSTRAINT_VIOLATION");
            });
    // Use an isolated encoder: application formatting/time/escaping settings cannot alter hashes.
    return JSON.valueToTree(value);
  }

  private static void sortPurchase(JsonNode purchase) {
    var object = (ObjectNode) purchase;
    for (var item : object.get("items")) sortArray((ObjectNode) item, "seats", "seatId");
    sortArray(object, "items", "orderItemId");
  }

  private static void sortArray(ObjectNode parent, String name, String id) {
    List<JsonNode> values = new ArrayList<>();
    parent.get(name).forEach(values::add);
    values.sort(Comparator.comparing(n -> n.get(id).asString()));
    var array = JSON.createArrayNode();
    values.forEach(array::add);
    parent.set(name, array);
  }

  private static byte[] bytes(JsonNode node) {
    try {
      var encoded =
          StandardCharsets.UTF_8
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(JSON.writeValueAsString(sortedKeys(node))));
      var bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return bytes;
    } catch (CharacterCodingException ex) {
      throw new ContractValidationException("$", "INVALID_UNICODE");
    }
  }

  private static JsonNode sortedKeys(JsonNode node) {
    if (node.isObject()) {
      var object = JSON.createObjectNode();
      node.propertyNames().stream()
          .sorted()
          .forEach(key -> object.set(key, sortedKeys(node.get(key))));
      return object;
    }
    if (node.isArray()) {
      var array = JSON.createArrayNode();
      node.forEach(value -> array.add(sortedKeys(value)));
      return array;
    }
    return node;
  }
}
