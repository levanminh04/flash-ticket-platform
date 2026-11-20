package com.flashticket.assistant_service.agent;

import com.flashticket.assistant_service.exception.AssistantProblem;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.http.HttpStatus;

final class AssistantSalesDates {
  private AssistantSalesDates() {
  }

  static LocalDate parseSalesDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException error) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT",
          "Ngày thống kê phải có định dạng yyyy-MM-dd.");
    }
  }

  static String validateTrendRange(LocalDate from, LocalDate to, String groupBy) {
    String grouping = groupBy == null ? "" : groupBy.trim().toUpperCase(Locale.ROOT);
    int maxDays = switch (grouping) {
      case "DAY" -> 31;
      case "WEEK" -> 180;
      case "MONTH" -> 366;
      default -> throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_SALES_GROUPING",
          "Chọn nhóm thống kê theo ngày, tuần hoặc tháng.");
    };
    // Tính cả ngày đầu và ngày cuối như báo cáo Booking.
    if (from == null || to == null || from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) >= maxDays) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
          "Khoảng ngày thiếu hoặc vượt giới hạn của nhóm thống kê.");
    }
    return grouping;
  }
}
