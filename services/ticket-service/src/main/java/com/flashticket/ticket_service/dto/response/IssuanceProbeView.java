package com.flashticket.ticket_service.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flashticket.ticket_service.dto.message.IssuanceResult;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "state",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = PendingIssuanceView.class, name = "PENDING"),
  @JsonSubTypes.Type(value = IssuanceResult.class, name = "COMPLETED"),
  @JsonSubTypes.Type(value = IssuanceResult.class, name = "FAILED")
})
public interface IssuanceProbeView {
  UUID orderId();

  UUID issuanceId();
}
