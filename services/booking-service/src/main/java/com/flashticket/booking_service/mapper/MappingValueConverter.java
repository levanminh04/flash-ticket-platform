package com.flashticket.booking_service.mapper;

import com.flashticket.booking_service.dto.snapshot.SeatSelectionSnapshot;
import com.flashticket.booking_service.dto.snapshot.SelectionSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Jackson JsonMapper is a configured, thread-safe Spring singleton dependency.")
public class MappingValueConverter {
  private final JsonMapper objectMapper;

  public MappingValueConverter(JsonMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toMoney(BigDecimal value) {
    if (value == null || value.signum() < 0)
      throw new IllegalArgumentException("Money must be non-negative");
    BigDecimal normalized;
    try {
      normalized = value.setScale(0, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Money must have scale zero", e);
    }
    String result = normalized.toPlainString();
    if (!result.matches("^(0|[1-9][0-9]{0,18})$"))
      throw new IllegalArgumentException("Money exceeds wire range");
    return result;
  }

  public String toUInt64(Long value) {
    if (value == null || value < 1) throw new IllegalArgumentException("Version must be positive");
    return Long.toString(value);
  }

  public List<SeatSelectionSnapshot> seats(SelectionSnapshot value) {
    return value == null ? List.of() : List.copyOf(value.seats());
  }

  public JsonNode toTree(Object value) {
    if (value == null) throw new IllegalArgumentException("Payload is required");
    return objectMapper.valueToTree(value);
  }

  public <T> T fromTree(JsonNode value, Class<T> type) {
    if (value == null || value.isNull()) throw new IllegalArgumentException("Payload is required");
    try {
      return objectMapper.treeToValue(value, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid typed payload", e);
    }
  }
}
