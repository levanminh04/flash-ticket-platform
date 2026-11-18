package com.flashticket.assistant_service.client;

import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.AssistantProblem;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class BuyerApiClient {
  private static final Logger log = LoggerFactory.getLogger(BuyerApiClient.class);

  private final RestClient buyerRestClient;
  private final JsonMapper jsonMapper;
  @Value("${assistant.event-base-url}") private URI eventBaseUrl;
  @Value("${assistant.booking-base-url}") private URI bookingBaseUrl;

  public EventPage searchEvents(Jwt jwt, EventSearchRequest request) {
    URI uri = UriComponentsBuilder.fromUri(eventBaseUrl).path("/api/v1/events")
        .queryParam("size", 5).queryParam("includePast", false)
        .queryParamIfPresent("q", Optional.ofNullable(request.getQ()))
        .queryParamIfPresent("venue", Optional.ofNullable(request.getVenue()))
        .queryParamIfPresent("categoryId", Optional.ofNullable(request.getCategoryId()))
        .queryParamIfPresent("from", Optional.ofNullable(request.getFrom()))
        .queryParamIfPresent("to", Optional.ofNullable(request.getTo()))
        .queryParamIfPresent("maxPrice", Optional.ofNullable(request.getMaxPrice()))
        .queryParamIfPresent("cursor", Optional.ofNullable(request.getCursor())).build().encode().toUri();
    EventPage page = get("searchEvents", uri, jwt, EventPage.class);
    if (page.getItems() == null || page.getItems().size() > 5) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Danh sách sự kiện không hợp lệ.");
    }
    page.setItems(page.getItems().stream().map(this::minimizeEvent).toList());
    if (jsonMapper.writeValueAsBytes(page).length > 16384) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "TOOL_RESULT_TOO_LARGE", "Kết quả quá lớn. Vui lòng thu hẹp bộ lọc.");
    }
    return page;
  }

  public EventView getEvent(Jwt jwt, UUID eventId) {
    EventView event = minimizeEvent(get("getEvent", UriComponentsBuilder.fromUri(eventBaseUrl)
        .path("/api/v1/events/" + eventId).build().toUri(), jwt, EventView.class));
    if (!eventId.equals(event.getEventId()) || jsonMapper.writeValueAsBytes(event).length > 16384) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Không đọc được thông tin sự kiện phù hợp.");
    }
    return event;
  }

  public AvailabilityReadView getAvailability(Jwt jwt, UUID eventId) {
    AvailabilityReadView availability = get("getAvailability", UriComponentsBuilder.fromUri(bookingBaseUrl)
        .path("/api/v1/events/" + eventId + "/availability").build().toUri(), jwt, AvailabilityReadView.class);
    if (!eventId.equals(availability.getEventId()) || availability.getAsOf() == null || availability.getTicketTypes() == null
        || jsonMapper.writeValueAsBytes(availability).length > 16384) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Không đọc được tồn vé phù hợp.");
    }
    return availability;
  }

  public OrderReadPage getMyOrders(Jwt jwt, int page, String state) {
    OrderReadPage result = get("getMyOrders", UriComponentsBuilder.fromUri(bookingBaseUrl).path("/api/v1/orders")
        .queryParam("page", page).queryParam("size", 5)
        .queryParamIfPresent("state", Optional.ofNullable(state).filter(value -> !value.isBlank()))
        .build().encode().toUri(), jwt, OrderReadPage.class);
    if (result.getItems() == null || result.getItems().size() > 5 || jsonMapper.writeValueAsBytes(result).length > 16384) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Không đọc được danh sách đơn phù hợp.");
    }
    return result;
  }

  public OrderReadView getMyOrder(Jwt jwt, UUID orderId) {
    OrderReadView order = get("getMyOrder", UriComponentsBuilder.fromUri(bookingBaseUrl)
        .path("/api/v1/orders/" + orderId).build().toUri(), jwt, OrderReadView.class);
    if (!orderId.equals(order.getOrderId()) || jsonMapper.writeValueAsBytes(order).length > 16384) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Không đọc được đơn phù hợp.");
    }
    return order;
  }

  private <T> T get(String operation, URI uri, Jwt jwt, Class<T> responseType) {
    // Base URL chỉ là origin cố định; model không thể chọn host/path/header.
    String uuid = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";
    boolean allowed = false;
    for (URI base : List.of(eventBaseUrl, bookingBaseUrl)) {
      if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
          || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
          || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
        throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_CONFIGURATION_INVALID", "Cấu hình dịch vụ chưa sẵn sàng.");
      }
      if (base.getScheme().equals(uri.getScheme()) && base.getRawAuthority().equals(uri.getRawAuthority())) {
        if (base.equals(eventBaseUrl) && uri.getPath().matches("/api/v1/events(?:/" + uuid + ")?")) allowed = true;
        if (base.equals(bookingBaseUrl) && (uri.getPath().matches("/api/v1/orders(?:/" + uuid + ")?")
            || uri.getPath().matches("/api/v1/events/" + uuid + "/availability"))) allowed = true;
      }
    }
    if (!allowed || uri.getUserInfo() != null || uri.getFragment() != null) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "DOWNSTREAM_PATH_NOT_ALLOWED", "Đường dẫn không được phép.");
    }
    if (jwt == null || jwt.getExpiresAt() == null || !Instant.now().isBefore(jwt.getExpiresAt())) {
      throw new AssistantProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
    }
    long startedNanos = System.nanoTime();
    try {
      T result = buyerRestClient.get().uri(uri).headers(headers -> {
        headers.setBearerAuth(jwt.getTokenValue()); headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Accept-Encoding", "identity");
      }).exchange((request, response) -> decodeResponse(response, responseType, operation, startedNanos));
      if (!Instant.now().isBefore(jwt.getExpiresAt())) {
        throw new AssistantProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
      }
      return result;
    } catch (AssistantProblem error) { throw error;
    } catch (Exception error) {
      Throwable cause = error.getCause();
      log.warn("Buyer API call failed: operation={} reasonCode=DOWNSTREAM_UNAVAILABLE errorType={} causeType={} durationMs={}",
          operation, error.getClass().getSimpleName(), cause == null ? "none" : cause.getClass().getSimpleName(),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE", "Dịch vụ dữ liệu chưa sẵn sàng.");
    }
  }

  private EventView minimizeEvent(EventView event) {
    if (event == null || event.getEventId() == null || event.getTitle() == null || event.getMode() == null
        || event.getTicketTypes() == null || event.getTicketTypes().size() > 100) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Thông tin sự kiện không hợp lệ.");
    }
    if (event.getTitle().length() > 300) event.setTitle(event.getTitle().substring(0, 300));
    if (event.getDescription() != null && event.getDescription().length() > 1000) event.setDescription(event.getDescription().substring(0, 1000));
    if (event.getVenueName() != null && event.getVenueName().length() > 200) event.setVenueName(event.getVenueName().substring(0, 200));
    for (EventView.TicketType ticket : event.getTicketTypes()) {
      if (ticket == null || ticket.getTicketTypeId() == null || ticket.getName() == null) {
        throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Thông tin loại vé không hợp lệ.");
      }
      if (ticket.getName().length() > 200) ticket.setName(ticket.getName().substring(0, 200));
    }
    return event;
  }

  private <T> T decodeResponse(ClientHttpResponse response, Class<T> responseType, String operation,
      long startedNanos) throws IOException {
    int status = response.getStatusCode().value();
    if (status != 200) {
      // Không đọc/echo body lỗi vì có thể chứa dữ liệu cá nhân hoặc credential.
      throw switch (status) {
        case 401 -> new AssistantProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
        case 403 -> new AssistantProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền đọc dữ liệu này.");
        case 404 -> new AssistantProblem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy dữ liệu được phép truy cập.");
        case 429 -> new AssistantProblem(HttpStatus.TOO_MANY_REQUESTS, "DOWNSTREAM_RATE_LIMITED", "Dịch vụ đang giới hạn yêu cầu.");
        default -> {
          log.warn("Buyer API call failed: operation={} reasonCode=DOWNSTREAM_UNAVAILABLE downstreamStatus={} durationMs={}",
              operation, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
          yield new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE", "Dịch vụ dữ liệu chưa sẵn sàng.");
        }
      };
    }
    String encoding = response.getHeaders().getFirst("Content-Encoding");
    if (encoding != null && !encoding.equalsIgnoreCase("identity") && !encoding.equalsIgnoreCase("gzip")) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_ENCODING", "Không đọc được phản hồi dịch vụ.");
    }
    InputStream body = response.getBody();
    if ("gzip".equalsIgnoreCase(encoding)) body = new GZIPInputStream(body);
    byte[] bytes = body.readNBytes(262145);
    if (bytes.length > 262144) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_BODY_TOO_LARGE", "Dữ liệu quá lớn. Vui lòng dùng trang sự kiện hoặc đơn hàng.");
    }
    try {
      T projected = jsonMapper.readValue(bytes, responseType);
      if (projected == null) throw new IllegalArgumentException();
      // Bỏ field ngoài DTO trước, rồi che email/Bearer/JWT nhận diện được trong text.
      String safeJson = jsonMapper.writeValueAsString(projected)
          .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "[REDACTED]")
          .replaceAll("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}", "[REDACTED]")
          .replaceAll("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+", "[REDACTED]");
      return jsonMapper.readValue(safeJson, responseType);
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_DOWNSTREAM_RESPONSE", "Không đọc được phản hồi dịch vụ.");
    }
  }
}
