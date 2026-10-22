package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractValidationException;

public final class VersionMapper {
  public Long toLong(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ex) {
      throw invalid();
    }
  }

  public String toWire(Long value) {
    if (value == null || value <= 0) throw invalid();
    return value.toString();
  }

  private static ContractValidationException invalid() {
    return new ContractValidationException("version", "INVALID_VERSION");
  }
}
