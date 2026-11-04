package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import java.math.BigDecimal;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public final class MoneyMapper {
  @Named("moneyToDecimal")
  public BigDecimal toDecimal(String value) {
    if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
    return new BigDecimal(value);
  }

  @Named("moneyToWire")
  public String toWire(BigDecimal value) {
    if (value == null) throw invalid();
    try {
      String wire = value.toBigIntegerExact().toString();
      toDecimal(wire);
      return wire;
    } catch (ArithmeticException ex) {
      throw invalid();
    }
  }

  private static ContractValidationException invalid() {
    return new ContractValidationException("amount", "INVALID_MONEY");
  }
}
