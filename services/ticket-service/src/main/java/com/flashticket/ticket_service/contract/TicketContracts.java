package com.flashticket.ticket_service.contract;

import java.lang.reflect.TypeVariable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Ticket's strict, version-one boundary. It deliberately has no dependency on other services. */
public final class TicketContracts {
  public static final String ACCESS = "event.access-configured.v1";
  public static final String CANCELLED = "event.cancelled.v1";
  public static final String ISSUE = "ticket.issue.v1";
  private static final Set<String> EVENT_STATES =
      Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "PUBLISHED", "CANCELLED");
  private static final Set<String> NULLABLE =
      Set.of("causationId", "traceparent", "venueName", "sectorId", "promotion");
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private TicketContracts() {}

  public record Envelope<T>(
      UUID messageId,
      String messageType,
      int schemaVersion,
      UUID aggregateId,
      String aggregateVersion,
      Instant occurredAt,
      UUID correlationId,
      UUID causationId,
      String traceparent,
      T data) {}

  public record AccessConfiguration(
      UUID eventId,
      String organizerSubject,
      String eventStatus,
      Instant eventStartsAt,
      Instant eventEndsAt,
      String sourceConfigVersion) {}

  public record EventCancellation(
      UUID eventId,
      UUID cancellationId,
      String sourceConfigVersion,
      Instant cancelledAt,
      String actorSubject,
      JsonNode sales,
      AccessConfiguration access) {
    public EventCancellation {
      sales = sales == null ? null : sales.deepCopy();
    }

    @Override
    public JsonNode sales() {
      return sales == null ? null : sales.deepCopy();
    }
  }

  public record IssueTickets(UUID orderId, UUID chargeId, PurchaseSnapshot purchase) {}

  public record PurchaseSnapshot(
      int schemaVersion,
      UUID orderId,
      UUID eventId,
      String buyerSubject,
      String customerEmail,
      String sourceConfigVersion,
      String sourceOrderVersion,
      Instant frozenAt,
      Instant expiresAt,
      String currency,
      String eventTitle,
      Instant eventStartsAt,
      Instant eventEndsAt,
      String venueName,
      String mode,
      UUID sectorId,
      String subtotal,
      String discount,
      String total,
      Promotion promotion,
      List<OrderItem> items) {
    public PurchaseSnapshot {
      items = items == null ? null : List.copyOf(items);
    }
  }

  public record OrderItem(
      UUID orderItemId,
      UUID ticketTypeId,
      String ticketTypeName,
      int quantity,
      String unitPrice,
      String lineSubtotal,
      UUID sectorId,
      List<Seat> seats) {
    public OrderItem {
      seats = seats == null ? null : List.copyOf(seats);
    }
  }

  public record Seat(UUID seatId, String seatLabel, String rowName, String seatNumber) {}

  public record Promotion(UUID promotionId, String code, String discount) {}

  public static final class InvalidContractException extends RuntimeException {
    private final String code;

    public InvalidContractException(String code) {
      super(code);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public static long version(String value) {
    require(value != null && value.matches("[1-9][0-9]{0,18}"));
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw invalid();
    }
  }

  public static void validateAccess(Envelope<AccessConfiguration> message) {
    validateEnvelope(message, ACCESS);
    var access = message.data();
    validateAccessData(access);
    require(message.aggregateId().equals(access.eventId()));
    require(message.aggregateVersion().equals(access.sourceConfigVersion()));
  }

  private static void validateAccessData(AccessConfiguration access) {
    if (access == null) throw invalid();
    require(access.eventId() != null);
    nonblank(access.organizerSubject());
    require(access.eventStatus() != null && EVENT_STATES.contains(access.eventStatus()));
    times(access.eventStartsAt(), access.eventEndsAt());
    version(access.sourceConfigVersion());
  }

  public static void validateCancellation(Envelope<EventCancellation> message) {
    validateEnvelope(message, CANCELLED);
    var cancellation = message.data();
    if (cancellation == null) throw invalid();
    require(
        cancellation.eventId() != null
            && cancellation.cancellationId() != null
            && cancellation.cancelledAt() != null);
    nonblank(cancellation.actorSubject());
    version(cancellation.sourceConfigVersion());
    validateAccessData(cancellation.access());
    require(
        message.aggregateId().equals(cancellation.eventId())
            && message.aggregateVersion().equals(cancellation.sourceConfigVersion())
            && cancellation.eventId().equals(cancellation.access().eventId())
            && cancellation
                .sourceConfigVersion()
                .equals(cancellation.access().sourceConfigVersion())
            && "CANCELLED".equals(cancellation.access().eventStatus()));
    JsonNode sales = cancellation.sales();
    require(
        sales != null
            && sales.isObject()
            && sales.path("schemaVersion").isIntegralNumber()
            && sales.path("schemaVersion").intValue() == 1
            && cancellation.eventId().toString().equals(sales.path("eventId").asString())
            && cancellation
                .sourceConfigVersion()
                .equals(sales.path("sourceConfigVersion").asString())
            && "CANCELLED".equals(sales.path("eventStatus").asString()));
  }

  public static void validateIssue(Envelope<IssueTickets> message) {
    validateEnvelope(message, ISSUE);
    var issue = message.data();
    if (issue == null) throw invalid();
    require(issue.orderId() != null && issue.chargeId() != null && issue.purchase() != null);
    require(
        message.aggregateId().equals(issue.orderId())
            && message.correlationId().equals(issue.orderId())
            && issue.orderId().equals(issue.purchase().orderId()));
    validatePurchase(issue.purchase());
  }

  private static void validateEnvelope(Envelope<?> message, String type) {
    if (message == null) throw invalid();
    require(
        message.messageId() != null
            && type.equals(message.messageType())
            && message.schemaVersion() == 1
            && message.aggregateId() != null
            && message.occurredAt() != null
            && message.correlationId() != null
            && message.data() != null);
    version(message.aggregateVersion());
    if (message.traceparent() != null) nonblank(message.traceparent());
  }

  private static void validatePurchase(PurchaseSnapshot purchase) {
    require(
        purchase.schemaVersion() == 1 && purchase.orderId() != null && purchase.eventId() != null);
    nonblank(purchase.buyerSubject());
    nonblank(purchase.eventTitle());
    require(
        purchase.customerEmail() != null
            && purchase.customerEmail().length() <= 254
            && purchase.customerEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"));
    version(purchase.sourceConfigVersion());
    version(purchase.sourceOrderVersion());
    times(purchase.frozenAt(), purchase.expiresAt());
    times(purchase.eventStartsAt(), purchase.eventEndsAt());
    require("VND".equals(purchase.currency()));
    boolean quantity = "QUANTITY".equals(purchase.mode());
    require(quantity || "SEAT_MAP".equals(purchase.mode()));
    require(quantity ? purchase.sectorId() == null : purchase.sectorId() != null);
    BigInteger subtotal = money(purchase.subtotal());
    BigInteger discount = money(purchase.discount());
    BigInteger total = money(purchase.total());
    require(total.signum() > 0 && subtotal.subtract(discount).equals(total));
    if (purchase.promotion() == null) require(discount.signum() == 0);
    else {
      require(purchase.promotion().promotionId() != null);
      nonblank(purchase.promotion().code());
      require(money(purchase.promotion().discount()).equals(discount));
    }
    require(purchase.items() != null && !purchase.items().isEmpty());
    Set<UUID> itemIds = new HashSet<>();
    Set<UUID> typeIds = new HashSet<>();
    Set<UUID> seatIds = new HashSet<>();
    BigInteger sum = BigInteger.ZERO;
    Boolean seated = null;
    for (var item : purchase.items()) {
      if (item == null) throw invalid();
      require(
          item.orderItemId() != null
              && itemIds.add(item.orderItemId())
              && item.ticketTypeId() != null
              && typeIds.add(item.ticketTypeId())
              && item.quantity() > 0
              && item.seats() != null);
      nonblank(item.ticketTypeName());
      BigInteger price = money(item.unitPrice());
      BigInteger line = money(item.lineSubtotal());
      require(
          price.signum() > 0 && price.multiply(BigInteger.valueOf(item.quantity())).equals(line));
      sum = sum.add(line);
      require(quantity ? item.sectorId() == null : purchase.sectorId().equals(item.sectorId()));
      boolean hasSeats = !item.seats().isEmpty();
      if (quantity) require(!hasSeats);
      else {
        if (seated != null) require(seated == hasSeats);
        seated = hasSeats;
        if (hasSeats) require(item.seats().size() == item.quantity());
      }
      for (var seat : item.seats()) {
        if (seat == null) throw invalid();
        require(seat.seatId() != null && seatIds.add(seat.seatId()));
        nonblank(seat.seatLabel());
        nonblank(seat.rowName());
        nonblank(seat.seatNumber());
      }
    }
    require(sum.equals(subtotal));
    ticketCount(purchase);
  }

  public static int ticketCount(PurchaseSnapshot purchase) {
    if (purchase == null) throw invalid();
    require(purchase.items() != null && !purchase.items().isEmpty());
    long count = 0;
    for (var item : purchase.items()) {
      if (item == null) throw invalid();
      require(item.quantity() > 0);
      count += item.quantity();
      require(count <= Integer.MAX_VALUE);
    }
    return (int) count;
  }

  private static BigInteger money(String value) {
    if (value == null) throw invalid();
    require(value.matches("(0|[1-9][0-9]{0,18})"));
    return new BigInteger(value);
  }

  private static void times(Instant first, Instant last) {
    require(first != null && last != null && first.isBefore(last));
  }

  /**
   * Decode using a boundary-specific mapper; application-wide coercion settings cannot weaken it.
   */
  public static <T> Envelope<T> decode(JsonMapper json, byte[] body, Class<T> dataType) {
    try {
      var strict =
          json.rebuild()
              .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
              .enable(
                  DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                  DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
              .build();
      var type = strict.getTypeFactory().constructParametricType(Envelope.class, dataType);
      JsonNode tree = strict.readTree(body);
      shape(strict, tree, type, false);
      return strict.treeToValue(tree, type);
    } catch (InvalidContractException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw invalid();
    }
  }

  private static void shape(JsonMapper json, JsonNode node, JavaType type, boolean nullable) {
    require(node != null);
    if (node.isNull()) {
      require(nullable);
      return;
    }
    Class<?> raw = type.getRawClass();
    if (raw == String.class) {
      require(node.isString());
      return;
    }
    if (raw == UUID.class) {
      require(node.isString() && node.asString().matches(UUID_PATTERN));
      return;
    }
    if (raw == Instant.class) {
      require(node.isString() && node.asString().endsWith("Z"));
      Instant.parse(node.asString());
      return;
    }
    if (raw == int.class) {
      require(node.isIntegralNumber() && node.canConvertToInt());
      return;
    }
    if (raw == JsonNode.class) {
      require(node.isObject());
      return;
    }
    if (type.isCollectionLikeType()) {
      require(node.isArray());
      for (var child : node) shape(json, child, type.getContentType(), false);
      return;
    }
    require(raw.isRecord() && node.isObject());
    Set<String> fields = new HashSet<>();
    for (var component : raw.getRecordComponents()) {
      String name = component.getName();
      fields.add(name);
      require(node.has(name));
      JavaType childType =
          component.getGenericType() instanceof TypeVariable<?> variable
              ? type.getBindings().findBoundType(variable.getName())
              : json.constructType(component.getGenericType());
      require(childType != null);
      shape(json, node.get(name), childType, NULLABLE.contains(name));
    }
    for (String name : node.propertyNames()) require(fields.contains(name));
  }

  public static String inputHash(JsonMapper json, IssueTickets issue) {
    return hash(json, issue);
  }

  public static String canonicalHash(JsonMapper json, Object value) {
    return hash(json, value);
  }

  public static String canonicalEnvelopeHash(JsonMapper json, Envelope<?> value) {
    return hash(json, value);
  }

  public static String hash(JsonMapper json, Object value) {
    try {
      String canonical = json.writeValueAsString(canonicalTree(json, tree(json, value), ""));
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable");
    } catch (RuntimeException ex) {
      throw invalid();
    }
  }

  private static JsonNode tree(JsonMapper json, Object value) {
    if (value == null) return json.nullNode();
    if (value instanceof JsonNode node) return node;
    if (value instanceof Instant instant)
      return json.getNodeFactory().stringNode(instant.toString());
    if (value instanceof List<?> list) {
      var array = json.createArrayNode();
      for (var element : list) array.add(tree(json, element));
      return array;
    }
    if (value.getClass().isRecord()) {
      ObjectNode object = json.createObjectNode();
      for (var component : value.getClass().getRecordComponents()) {
        try {
          object.set(component.getName(), tree(json, component.getAccessor().invoke(value)));
        } catch (ReflectiveOperationException ex) {
          throw invalid();
        }
      }
      return object;
    }
    return json.valueToTree(value);
  }

  private static JsonNode canonicalTree(JsonMapper json, JsonNode node, String name) {
    if (node.isObject()) {
      var object = json.createObjectNode();
      node.propertyNames().stream()
          .sorted()
          .forEach(key -> object.set(key, canonicalTree(json, node.get(key), key)));
      return object;
    }
    if (node.isArray()) {
      var values = new ArrayList<JsonNode>();
      for (var child : node) values.add(canonicalTree(json, child, ""));
      String key = "items".equals(name) ? "orderItemId" : "seats".equals(name) ? "seatId" : null;
      if (key != null) values.sort(Comparator.comparing(child -> child.path(key).asString()));
      var array = json.createArrayNode();
      values.forEach(array::add);
      return array;
    }
    if (node.isString() && name.endsWith("At")) {
      try {
        return json.getNodeFactory().stringNode(Instant.parse(node.asString()).toString());
      } catch (java.time.format.DateTimeParseException ex) {
        throw invalid();
      }
    }
    return node;
  }

  private static void nonblank(String value) {
    require(value != null && !value.isBlank());
  }

  private static void require(boolean condition) {
    if (!condition) throw invalid();
  }

  private static InvalidContractException invalid() {
    return new InvalidContractException("INVALID_CONTRACT");
  }
}
