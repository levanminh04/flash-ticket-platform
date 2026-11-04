package com.flashticket.ticket_service.mapper;

import com.flashticket.ticket_service.validation.ContractRules;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JsonMapper is a configured thread-safe Spring singleton.")
public class MappingValueConverter {
  private final JsonMapper json;

  public MappingValueConverter(JsonMapper jsonMapper) {
    this.json = jsonMapper;
  }

  public String toUInt64(Long value) {
    if (value == null || value < 1) throw new IllegalArgumentException("Version must be positive");
    return value.toString();
  }

  public Long parseUInt64(String value) {
    if (!ContractRules.validUInt64(value)) throw new IllegalArgumentException("Invalid version");
    return Long.valueOf(value);
  }

  public JsonNode toTree(Object value) {
    if (value == null) throw new IllegalArgumentException("Payload is required");
    return json.valueToTree(value);
  }

  public <T> T fromTree(JsonNode value, Class<T> type) {
    if (value == null || value.isNull()) throw new IllegalArgumentException("Payload is required");
    try {
      return json.treeToValue(value, type);
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalArgumentException("Invalid typed payload", e);
    }
  }
}
