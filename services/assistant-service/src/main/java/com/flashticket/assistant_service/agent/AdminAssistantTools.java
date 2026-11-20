package com.flashticket.assistant_service.agent;

import com.flashticket.assistant_service.client.AssistantApiClient;
import com.flashticket.assistant_service.dto.OrganizerEventSalesPage;
import com.flashticket.assistant_service.dto.OrganizerEventSalesSummary;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AdminAssistantTools {
  private final AssistantApiClient apiClient;
  private final RedisConversationStore conversationStore;
  private final Clock clock;
  private final Jwt jwt;
  private final String ownerKey;
  private final UUID conversationId;
  private final UUID leaseToken;
  private final Instant deadline;
  private int executions;

  public AdminAssistantTools(AssistantApiClient apiClient, RedisConversationStore conversationStore,
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

  @Tool("Tổng số đơn bán, số vé bán và doanh thu bán vé toàn hệ thống")
  public OrganizerSalesSummary getPlatformSalesSummary(
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    return apiClient.getPlatformSalesSummary(jwt, AssistantSalesDates.parseSalesDate(from),
        AssistantSalesDates.parseSalesDate(to));
  }

  @Tool("Xu hướng bán vé toàn hệ thống. DAY tối đa 31 ngày, WEEK 180 ngày, MONTH 366 ngày")
  public OrganizerSalesTrend getPlatformSalesTrend(
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd") String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd") String to,
      @P(name = "groupBy", value = "DAY, WEEK hoặc MONTH") String groupBy) {
    beforeTool();
    LocalDate fromDate = AssistantSalesDates.parseSalesDate(from);
    LocalDate toDate = AssistantSalesDates.parseSalesDate(to);
    String grouping = AssistantSalesDates.validateTrendRange(fromDate, toDate, groupBy);
    return apiClient.getPlatformSalesTrend(jwt, fromDate, toDate, grouping);
  }

  @Tool("Top 5 sự kiện toàn hệ thống theo doanh thu bán vé giảm dần")
  public OrganizerEventSalesPage getTopEventsBySales(
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    return apiClient.getPlatformTopEvents(jwt, AssistantSalesDates.parseSalesDate(from),
        AssistantSalesDates.parseSalesDate(to));
  }

  @Tool("Thống kê bán vé của một sự kiện và tối đa 10 hạng vé có số lượng bán cao nhất")
  public OrganizerEventSalesSummary getEventSales(
      @P(name = "eventId", value = "UUID sự kiện") UUID eventId,
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    if (eventId == null) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_INPUT", "Cần mã sự kiện hợp lệ.");
    }
    return apiClient.getAdminEventSales(jwt, eventId, AssistantSalesDates.parseSalesDate(from),
        AssistantSalesDates.parseSalesDate(to));
  }

  @Tool("Tổng số đơn bán, số vé bán và doanh thu bán vé của một nhà tổ chức")
  public OrganizerSalesSummary getOrganizerSalesSummary(
      @P(name = "organizerSubject", value = "UUID nhà tổ chức") UUID organizerSubject,
      @P(name = "from", value = "Ngày bắt đầu ISO yyyy-MM-dd", required = false) String from,
      @P(name = "to", value = "Ngày kết thúc ISO yyyy-MM-dd", required = false) String to) {
    beforeTool();
    if (organizerSubject == null) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_TOOL_INPUT", "Cần mã nhà tổ chức hợp lệ.");
    }
    return apiClient.getAdminOrganizerSalesSummary(jwt, organizerSubject, AssistantSalesDates.parseSalesDate(from),
        AssistantSalesDates.parseSalesDate(to));
  }

  @Tool("Liệt kê các sự kiện chờ duyệt, tối đa 5 sự kiện mỗi trang")
  public OrganizerOwnedEventPage listEventsPendingReview(
      @P(name = "cursor", value = "nextCursor từ kết quả trước", required = false) String cursor) {
    beforeTool();
    return apiClient.listAdminEventsPendingReview(jwt, cursor);
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
}
