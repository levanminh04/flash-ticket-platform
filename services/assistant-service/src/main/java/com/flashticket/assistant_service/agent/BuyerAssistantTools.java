package com.flashticket.assistant_service.agent;

import com.flashticket.assistant_service.client.BuyerApiClient;
import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.flashticket.assistant_service.service.RedisConversationStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public final class BuyerAssistantTools {
  private final BuyerApiClient buyerApiClient;
  private final RedisConversationStore conversationStore;
  private final Validator validator;
  private final Clock clock;
  private final Jwt jwt;
  private final String ownerKey;
  private final UUID conversationId;
  private final UUID leaseToken;
  private final Instant deadline;
  private int executions;
  private PurchaseDraft purchaseDraft;
  private final List<EventView> referencedEvents = new ArrayList<>();
  private List<OrderReadView> referencedOrders;

  public BuyerAssistantTools(BuyerApiClient buyerApiClient, RedisConversationStore conversationStore, Validator validator,
      Clock clock, Jwt jwt, String ownerKey, UUID conversationId, UUID leaseToken, Instant deadline) {
    this.buyerApiClient = buyerApiClient; this.conversationStore = conversationStore;
    this.validator = validator; this.clock = clock; this.jwt = jwt; this.ownerKey = ownerKey;
    this.conversationId = conversationId; this.leaseToken = leaseToken; this.deadline = deadline;
  }

  @Tool
  public EventPage searchEvents(@P("Bộ lọc sự kiện công khai") EventSearchRequest request) {
    beforeTool(); requireValidToolInput(request);
    if (request.getFrom() != null && request.getTo() != null && request.getFrom().isAfter(request.getTo())) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_EVENT_FILTER", "Khoảng ngày không hợp lệ.");
    }
    EventPage page = buyerApiClient.searchEvents(jwt, request);
    for (EventView event : page.getItems()) {
      referencedEvents.removeIf(previous -> previous.getEventId().equals(event.getEventId()));
      referencedEvents.add(event);
      if (referencedEvents.size() > 5) referencedEvents.removeFirst();
    }
    return page;
  }

  @Tool
  public EventView getEvent(@P("UUID sự kiện") UUID eventId) {
    beforeTool(); requireValidToolInput(eventId);
    EventView event = buyerApiClient.getEvent(jwt, eventId);
    referencedEvents.removeIf(previous -> previous.getEventId().equals(eventId));
    referencedEvents.add(event);
    if (referencedEvents.size() > 5) referencedEvents.removeFirst();
    return event;
  }

  @Tool
  public AvailabilityReadView getAvailability(@P("UUID sự kiện") UUID eventId) {
    beforeTool(); requireValidToolInput(eventId);
    EventView event = buyerApiClient.getEvent(jwt, eventId);
    referencedEvents.removeIf(previous -> previous.getEventId().equals(eventId));
    referencedEvents.add(event);
    if (referencedEvents.size() > 5) referencedEvents.removeFirst();
    if (!"QUANTITY".equals(event.getMode())) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "USE_EVENT_SELECTION_UI", "Sự kiện này cần dùng trang chọn khu vực hoặc ghế hiện có.");
    }
    return buyerApiClient.getAvailability(jwt, eventId);
  }

  @Tool
  public OrderReadPage getMyOrders(@P("Trang từ 0") int page, @P("Trạng thái hoặc chuỗi rỗng") String state) {
    beforeTool();
    if (page < 0 || page > 10000 || (state != null && !state.isBlank()
        && !Set.of("PENDING_PAYMENT", "ISSUING", "COMPLETED", "CANCELLED", "EXPIRED", "ISSUANCE_FAILED").contains(state))) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_ORDER_FILTER", "Bộ lọc đơn không hợp lệ.");
    }
    OrderReadPage result = buyerApiClient.getMyOrders(jwt, page, state);
    Set<UUID> seen = new HashSet<>();
    List<OrderReadView> orders = new ArrayList<>();
    for (OrderReadView order : result.getItems()) {
      if (order != null && order.getOrderId() != null && seen.add(order.getOrderId())) {
        orders.add(order);
        if (orders.size() == 5) break;
      }
    }
    referencedOrders = List.copyOf(orders);
    return result;
  }

  @Tool
  public OrderReadView getMyOrder(@P("UUID đơn") UUID orderId) {
    beforeTool(); requireValidToolInput(orderId);
    OrderReadView order = buyerApiClient.getMyOrder(jwt, orderId);
    referencedOrders = List.of(order);
    return order;
  }

  @Tool
  public PurchaseDraft prepareQuantityPurchase(@P("UUID sự kiện") UUID eventId, @P("Loại vé và số lượng") List<QuantityLine> items) {
    beforeTool(); requireValidToolInput(eventId);
    if (purchaseDraft != null || items == null || items.isEmpty() || items.size() > 10) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_PURCHASE_DRAFT", "Chỉ chuẩn bị một form với tối đa 10 loại vé trong mỗi lượt.");
    }
    Set<UUID> requestedTicketIds = new HashSet<>();
    for (QuantityLine line : items) {
      requireValidToolInput(line);
      if (!requestedTicketIds.add(line.getTicketTypeId())) {
        throw new AssistantProblem(HttpStatus.BAD_REQUEST, "DUPLICATE_TICKET_TYPE", "Mỗi loại vé chỉ xuất hiện một lần.");
      }
    }
    EventView event = buyerApiClient.getEvent(jwt, eventId);
    if (!"QUANTITY".equals(event.getMode())) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "USE_EVENT_SELECTION_UI", "Sự kiện này cần dùng trang chọn khu vực hoặc ghế hiện có.");
    }
    AvailabilityReadView availability = buyerApiClient.getAvailability(jwt, eventId);
    BigDecimal estimatedTotal = BigDecimal.ZERO;
    String currency = null;
    List<EventView.TicketType> selectedTypes = new ArrayList<>();
    for (QuantityLine line : items) {
      List<EventView.TicketType> matchingTypes = event.getTicketTypes().stream()
          .filter(ticket -> line.getTicketTypeId().equals(ticket.getTicketTypeId())).toList();
      List<AvailabilityReadView.TicketStock> matchingStocks = availability.getTicketTypes().stream()
          .filter(stock -> stock != null && line.getTicketTypeId().equals(stock.getTicketTypeId())).toList();
      if (matchingTypes.size() != 1 || matchingStocks.size() != 1) {
        throw new AssistantProblem(HttpStatus.BAD_REQUEST, "TICKET_TYPE_NOT_AVAILABLE", "Loại vé không thuộc sự kiện hoặc chưa có tồn tham khảo.");
      }
      EventView.TicketType ticket = matchingTypes.getFirst();
      String available = matchingStocks.getFirst().getAvailable();
      if (ticket.getUnitPrice() == null || !ticket.getUnitPrice().matches("(?:0|[1-9][0-9]{0,17})(?:\\.[0-9]{1,6})?")
          || ticket.getCurrency() == null || !ticket.getCurrency().matches("[A-Z]{3}")
          || available == null || !available.matches("0|[1-9][0-9]{0,18}")) {
        throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Giá hoặc tồn tham khảo chưa hợp lệ.");
      }
      if (new BigDecimal(available).compareTo(BigDecimal.valueOf(line.getQuantity())) < 0) {
        throw new AssistantProblem(HttpStatus.CONFLICT, "INSUFFICIENT_AVAILABILITY", "Tồn tham khảo không đủ. Vui lòng chọn lại số lượng.");
      }
      if (currency != null && !currency.equals(ticket.getCurrency())) {
        throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INCONSISTENT_CURRENCY", "Không thể tạo form cho các loại tiền khác nhau.");
      }
      currency = ticket.getCurrency();
      estimatedTotal = estimatedTotal.add(new BigDecimal(ticket.getUnitPrice()).multiply(BigDecimal.valueOf(line.getQuantity())));
      selectedTypes.add(ticket);
    }
    // Chỉ tạo dữ liệu form; Booking vẫn kiểm lại giá/tồn/giới hạn lúc user xác nhận.
    PurchaseDraft draft = new PurchaseDraft();
    draft.setDraftId(UUID.randomUUID()); draft.setEventId(eventId); draft.setEventTitle(event.getTitle());
    draft.setEstimatedTotal(estimatedTotal.toPlainString()); draft.setCurrency(currency);
    draft.setItems(List.copyOf(items)); draft.setTicketTypes(List.copyOf(selectedTypes));
    draft.setAvailabilityAsOf(availability.getAsOf()); draft.setExpiresAt(clock.instant().plusSeconds(600));
    purchaseDraft = draft;
    referencedEvents.removeIf(previous -> previous.getEventId().equals(eventId));
    referencedEvents.add(event);
    if (referencedEvents.size() > 5) referencedEvents.removeFirst();
    return draft;
  }

  private void beforeTool() {
    if (++executions > 6) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "TOOL_EXECUTION_LIMIT", "Đã đạt giới hạn tra cứu trong lượt này.");
    }
    if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline)) {
      throw new AssistantProblem(HttpStatus.GATEWAY_TIMEOUT, "TURN_DEADLINE_EXCEEDED", "Lượt trò chuyện đã hết thời gian.");
    }
    if (jwt.getExpiresAt() == null || !clock.instant().isBefore(jwt.getExpiresAt())) {
      throw new AssistantProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
    }
    if (!conversationStore.isLeaseCurrent(ownerKey, conversationId, leaseToken)) {
      throw new AssistantProblem(HttpStatus.CONFLICT, "LEASE_LOST", "Hội thoại đã thay đổi. Vui lòng đọc lại trạng thái.");
    }
  }

  private void requireValidToolInput(Object input) {
    if (input == null || !validator.validate(input).isEmpty()) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_INPUT", "Thông tin tra cứu không hợp lệ.");
    }
  }

  public PurchaseDraft getPurchaseDraft() { return purchaseDraft; }

  public List<EventView> getReferencedEvents() { return List.copyOf(referencedEvents); }

  public List<OrderReadView> getReferencedOrders() { return referencedOrders; }
}
