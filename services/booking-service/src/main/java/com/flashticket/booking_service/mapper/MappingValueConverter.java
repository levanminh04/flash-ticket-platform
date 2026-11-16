package com.flashticket.booking_service.mapper;

import com.flashticket.booking_service.dto.snapshot.SeatSelectionSnapshot;
import com.flashticket.booking_service.entity.OrderItem;
import com.flashticket.booking_service.enums.SalesMode;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Context;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
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
      throw new BusinessException(ErrorCode.INVALID_MONEY_VALUE);
    BigDecimal normalized;
    try {
      normalized = value.setScale(0, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new BusinessException(ErrorCode.INVALID_MONEY_VALUE, e);
    }
    String result = normalized.toPlainString();
    if (!result.matches("^(0|[1-9][0-9]{0,18})$"))
      throw new BusinessException(ErrorCode.INVALID_MONEY_VALUE);
    return result;
  }

  public String toUInt64(Long value) {
    if (value == null || value < 1) throw new BusinessException(ErrorCode.INVALID_VERSION);
    return Long.toString(value);
  }

  public List<SeatSelectionSnapshot> seats(
      OrderItem item, @Context Map<UUID, UUID> standingSectorEventIds) {
    var snapshot = item.getSelectionSnapshot();
    var seats = snapshot == null ? null : snapshot.seats();
    if (seats != null && !seats.isEmpty() && seats.stream().noneMatch(Objects::isNull))
      return List.copyOf(seats);
    // Chỉ dữ liệu cũ của vé không ghế mới được chuẩn hóa null thành [].
    if ((seats == null || seats.isEmpty()) && item.getSalesMode() == SalesMode.QUANTITY)
      return List.of();
    if ((seats == null || seats.isEmpty())
        && item.getSalesMode() == SalesMode.SEAT_MAP
        && item.getSectorId() != null
        && item.getEventId() != null
        && item.getEventId().equals(standingSectorEventIds.get(item.getSectorId())))
      return List.of();
    // Không che dữ liệu ghế bị mất hoặc sector không xác định bằng danh sách rỗng.
    log.error(
        "Invalid order selection snapshot: orderId={}, orderItemId={}",
        item.getOrder() == null ? null : item.getOrder().getId(),
        item.getId());
    throw new BusinessException(ErrorCode.INTERNAL_ERROR);
  }

  public JsonNode toTree(Object value) {
    if (value == null) throw new BusinessException(ErrorCode.INVALID_MESSAGE_PAYLOAD);
    return objectMapper.valueToTree(value);
  }

  public <T> T fromTree(JsonNode value, Class<T> type) {
    if (value == null || value.isNull())
      throw new BusinessException(ErrorCode.INVALID_MESSAGE_PAYLOAD);
    try {
      return objectMapper.treeToValue(value, type);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.INVALID_MESSAGE_PAYLOAD, e);
    }
  }
}
