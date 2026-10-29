package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.exception.BusinessException;
import com.flashticket.payment_service.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class WireValueConverter {
  @Named("toMoney")
  public String toMoney(BigDecimal value) {
    if (value == null || value.signum() < 0)
      throw new BusinessException(ErrorCode.INVALID_MONEY_VALUE);
    String result;
    try {
      result = value.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    } catch (ArithmeticException exception) {
      throw new BusinessException(ErrorCode.INVALID_MONEY_VALUE, exception);
    }
    if (!result.matches("^(0|[1-9][0-9]{0,18})$"))
      throw new BusinessException(ErrorCode.INVALID_MONEY_VALUE);
    return result;
  }
}
