package com.flashticket.assistant_service.agent;

import com.flashticket.assistant_service.client.AssistantApiClient;
import com.flashticket.assistant_service.dto.OrganizerEventSalesPage;
import com.flashticket.assistant_service.dto.OrganizerEventSalesSummary;
import com.flashticket.assistant_service.dto.OrganizerEventView;
import com.flashticket.assistant_service.dto.OrganizerOwnedEventPage;
import com.flashticket.assistant_service.dto.OrganizerSalesSummary;
import com.flashticket.assistant_service.dto.OrganizerSalesTrend;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.flashticket.assistant_service.service.RedisConversationStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public final class OrganizerAssistantTools {
  private final AssistantApiClient apiClient;
  private final RedisConversationStore conversationStore;
  private final Clock clock;
  private final Jwt jwt;
  private final String ownerKey;
  private final UUID conversationId;
  private final UUID leaseToken;
  private final Instant deadline;
  private int executions;

  public OrganizerAssistantTools(AssistantApiClient apiClient, RedisConversationStore conversationStore,
      Clock clock, Jwt jwt, String ownerKey, UUID conversationId, UUID leaseToken, Instant deadline) {
    this.apiClient = apiClient;
    this.conversationStore = conversationStore;
    this.clock = clock;
    this.jwt = jwt;
    this.ownerKey = ownerKey;
    this.conversationId = conversationId;
    this.leaseToken = leaseToken;
    this.deadline = deadline;
  }

  @Tool("Tổng số đơn bán, số vé bán và doanh thu bán vé của organizer")
  public OrganizerSalesSummary getMySalesSummary(
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    return apiClient.getOrganizerSalesSummary(jwt, parseSalesDate(from), parseSalesDate(to));
  }

  @Tool("Top 5 sự kiện của organizer theo doanh thu bán vé giảm dần")
  public OrganizerEventSalesPage getMyTopEvents(
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    return apiClient.getOrganizerTopEvents(jwt, parseSalesDate(from), parseSalesDate(to));
  }

  @Tool("Liệt kê từng trang 5 sự kiện của organizer và lọc theo trạng thái")
  public OrganizerOwnedEventPage listMyEvents(
      @P(name = "status", value = "DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED hoặc CANCELLED",
          required = false) String status,
      @P(name = "cursor", value = "nextCursor từ kết quả trước", required = false) String cursor) {
    beforeTool();
    return apiClient.listOrganizerEvents(jwt, status, cursor);
  }

  @Tool("Xem trạng thái, lịch diễn ra và thời gian mở bán của một sự kiện do organizer sở hữu")
  public OrganizerEventView getMyEvent(@P(name = "eventId", value = "UUID sự kiện") UUID eventId) {
    beforeTool();
    if (eventId == null) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_INPUT", "Cần mã sự kiện hợp lệ.");
    }
    return apiClient.getOrganizerEvent(jwt, eventId);
  }

  @Tool("Xu hướng bán vé theo kỳ. DAY tối đa 31 ngày, WEEK 180 ngày, MONTH 366 ngày")
  public OrganizerSalesTrend getMySalesTrend(
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd") String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd") String to,
      @P(name = "groupBy", value = "DAY, WEEK hoặc MONTH") String groupBy) {
    beforeTool();
    LocalDate fromDate = parseSalesDate(from);
    LocalDate toDate = parseSalesDate(to);
    String grouping = groupBy == null ? "" : groupBy.trim().toUpperCase(Locale.ROOT);
    int maxDays = switch (grouping) {
      case "DAY" -> 31;
      case "WEEK" -> 180;
      case "MONTH" -> 366;
      default -> throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_SALES_GROUPING",
          "Chọn nhóm thống kê theo ngày, tuần hoặc tháng.");
    };
    // Tính cả ngày đầu và ngày cuối như báo cáo Booking.
    if (fromDate == null || toDate == null || fromDate.isAfter(toDate)
        || ChronoUnit.DAYS.between(fromDate, toDate) >= maxDays) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
          "Khoảng ngày thiếu hoặc vượt giới hạn của nhóm thống kê.");
    }
    return apiClient.getOrganizerSalesTrend(jwt, fromDate, toDate, grouping);
  }

  @Tool("Thống kê bán vé của một sự kiện và tối đa 10 hạng vé có số lượng bán cao nhất")
  public OrganizerEventSalesSummary getMyEventSales(
      @P(name = "eventId", value = "UUID sự kiện") UUID eventId,
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    if (eventId == null) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_INPUT", "Cần mã sự kiện hợp lệ.");
    }
    return apiClient.getOrganizerEventSales(jwt, eventId, parseSalesDate(from), parseSalesDate(to));
  }

  private void beforeTool() {
    if (++executions > 6) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "TOOL_EXECUTION_LIMIT",
          "Đã đạt giới hạn tra cứu trong lượt này.");
    }
    Instant now = clock.instant();
    if (Thread.currentThread().isInterrupted() || !now.isBefore(deadline)) {
      throw new AssistantProblem(HttpStatus.GATEWAY_TIMEOUT, "TURN_DEADLINE_EXCEEDED",
          "Lượt trò chuyện đã hết thời gian.");
    }
    if (jwt.getExpiresAt() == null || !now.isBefore(jwt.getExpiresAt())) {
      throw new AssistantProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
    }
    if (!conversationStore.isLeaseCurrent(ownerKey, conversationId, leaseToken)) {
      throw new AssistantProblem(HttpStatus.CONFLICT, "LEASE_LOST",
          "Hội thoại đã thay đổi. Vui lòng đọc lại trạng thái.");
    }
  }

  private LocalDate parseSalesDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException error) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT",
          "Ngày thống kê phải có định dạng yyyy-MM-dd.");
    }
  }
}
