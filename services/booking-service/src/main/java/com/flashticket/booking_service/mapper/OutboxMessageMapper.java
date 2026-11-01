package com.flashticket.booking_service.mapper;

import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.entity.OutboxMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = MappingValueConverter.class)
public abstract class OutboxMessageMapper {
  @Autowired protected MappingValueConverter converter;
  @Autowired protected Validator validator;

  @Mapping(target = "payload", source = "data")
  @Mapping(
      target = "aggregateVersion",
      expression = "java(parseAggregateVersion(envelope.aggregateVersion()))")
  @Mapping(target = "publishedAt", ignore = true)
  @Mapping(target = "attempts", constant = "0")
  @Mapping(target = "nextAttemptAt", source = "occurredAt")
  @Mapping(target = "lastErrorCode", ignore = true)
  protected abstract OutboxMessage mapToEntity(Envelope<?> envelope);

  protected Long parseAggregateVersion(String value) {
    if (!com.flashticket.booking_service.validation.ContractRules.validUInt64(value))
      throw new IllegalArgumentException("aggregateVersion is out of range");
    return Long.valueOf(value);
  }

  public OutboxMessage toEntity(Envelope<?> envelope) {
    validateEnvelope(envelope);
    return mapToEntity(envelope);
  }

  public <T> Envelope<T> toEnvelope(OutboxMessage row, Class<T> payloadType) {
    if (row == null
        || row.getSchemaVersion() == null
        || row.getSchemaVersion() != 1
        || row.getAggregateVersion() == null
        || row.getAggregateVersion() < 1)
      throw new IllegalArgumentException("Unsupported schemaVersion");
    if (!com.flashticket.booking_service.validation.ContractRules.matchesPayload(
        row.getMessageType(), payloadType))
      throw new IllegalArgumentException("messageType does not match payload type");
    T data = converter.fromTree(row.getPayload(), payloadType);
    Envelope<T> result =
        new Envelope<>(
            row.getMessageId(),
            row.getMessageType(),
            row.getSchemaVersion(),
            row.getAggregateId(),
            row.getAggregateVersion().toString(),
            row.getOccurredAt(),
            row.getCorrelationId(),
            row.getCausationId(),
            row.getTraceparent(),
            data);
    validateEnvelope(result);
    return result;
  }

  private void validateEnvelope(Envelope<?> envelope) {
    if (envelope == null) throw new IllegalArgumentException("Envelope is required");
    Set<ConstraintViolation<Envelope<?>>> violations = validator.validate(envelope);
    if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
  }
}
