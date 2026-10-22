package com.flashticket.payment_service.mapper;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class MessageJsonMapperTest {
  @Test
  void preservesEnvelopeAndInboxResultWithoutGeneratingMetadata() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var mapper =
          new MessageJsonMapper(
              new ContractCodec(JsonMapper.builder().build(), factory.getValidator()));
      var id = UUID.randomUUID();
      var order = UUID.randomUUID();
      var result =
          new StartPaymentResult(
              id, order, StartPaymentResultState.REJECTED, null, "ORDER_EXPIRED");
      assertThat(mapper.fromResultJson(mapper.toResultJson(result))).isEqualTo(result);
      var envelope =
          new Envelope<>(
              id, "fixture.type.v1", 1, order, "1", Instant.EPOCH, order, null, null, result);
      assertThat(
              mapper.fromEnvelopeJson(
                  mapper.toEnvelopeJson(envelope),
                  new TypeReference<Envelope<StartPaymentResult>>() {}))
          .isEqualTo(envelope);
    }
  }
}
