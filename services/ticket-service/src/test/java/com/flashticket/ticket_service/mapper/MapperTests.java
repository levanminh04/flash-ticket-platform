package com.flashticket.ticket_service.mapper;

import static com.flashticket.ticket_service.support.TicketFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.dto.message.*;
import com.flashticket.ticket_service.enums.IssuanceState;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

class MapperTests {
  static AnnotationConfigApplicationContext context;
  static TicketMapper tickets;
  static IssuanceMapper issuances;
  static OutboxMessageMapper outbox;
  static MappingValueConverter converter;

  @BeforeAll
  static void setup() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(JsonMapper.class, () -> jsonMapper());
    context.register(LocalValidatorFactoryBean.class);
    context.scan("com.flashticket.ticket_service.mapper");
    context.refresh();
    tickets = context.getBean(TicketMapper.class);
    issuances = context.getBean(IssuanceMapper.class);
    outbox = context.getBean(OutboxMessageMapper.class);
    converter = context.getBean(MappingValueConverter.class);
  }

  @AfterAll
  static void close() {
    if (context != null) context.close();
  }

  @Test
  void restoresOutboxEnvelopeWithoutChangingIdentity() {
    var original = resultEnvelope();
    var row = outbox.toEntity(original);
    assertThat(outbox.toEnvelope(row, IssuanceResult.class)).isEqualTo(original);
    assertThat(row.getMessageId()).isEqualTo(MESSAGE);
    assertThat(row.getOccurredAt()).isEqualTo(NOW);
    assertThat(row.getAttempts()).isZero();
    assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
    row.setAttempts(7);
    row.setNextAttemptAt(NOW.plusSeconds(300));
    assertThat(outbox.toEnvelope(row, IssuanceResult.class)).isEqualTo(original);
  }

  @Test
  void exposesOnlyTicketView() {
    var view = tickets.toView(ticket());
    assertThat(view.ticketTypeName()).isEqualTo("Standard");
    assertThat(view.seatLabel()).isNull();
    assertThat(jsonMapper().writeValueAsString(view))
        .doesNotContain("TEST_HASH", "TEST_ONLY", "holderSubject", "qrTokenCiphertext");
  }

  @Test
  void nonCompletedIssuanceExposesNoTickets() {
    for (var state : java.util.List.of(IssuanceState.PENDING, IssuanceState.FAILED)) {
      var issuance = completedIssuance();
      issuance.setState(state);
      assertThat(issuances.toView(issuance).tickets()).isEmpty();
      assertThat(issuance.getState()).isEqualTo(state);
    }
    assertThat(issuances.toView(completedIssuance()).tickets()).hasSize(2);
  }

  @Test
  void completedIssuanceRequiresExpectedCount() {
    var issuance = completedIssuance();
    issuance.getTickets().removeLast();
    assertThatThrownBy(() -> issuances.toView(issuance))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidTicketCannotProduceAView() {
    var value = ticket();
    value.setDisplaySnapshot(null);
    assertThatThrownBy(() -> tickets.toView(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMismatchedOutboxTypeAndSchema() {
    var row = outbox.toEntity(resultEnvelope());
    assertThatThrownBy(() -> outbox.toEnvelope(row, IssueTickets.class))
        .isInstanceOf(IllegalArgumentException.class);
    row.setSchemaVersion(2);
    assertThatThrownBy(() -> outbox.toEnvelope(row, IssuanceResult.class))
        .isInstanceOf(IllegalArgumentException.class);
    row.setSchemaVersion(1);
    row.setAggregateVersion(0L);
    assertThatThrownBy(() -> outbox.toEnvelope(row, IssuanceResult.class))
        .isInstanceOf(IllegalArgumentException.class);
    row.setAggregateVersion(1L);
    row.setCorrelationId(null);
    assertThatThrownBy(() -> outbox.toEnvelope(row, IssuanceResult.class))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void versionConversionKeepsSignedBigintBoundary() {
    assertThat(converter.toUInt64(Long.MAX_VALUE)).isEqualTo("9223372036854775807");
    assertThat(converter.parseUInt64("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    for (Long value : Arrays.asList(null, 0L, -1L))
      assertThatThrownBy(() -> converter.toUInt64(value))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> converter.parseUInt64("9223372036854775808"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void typedJsonRejectsMissingOrMalformedPayload() {
    assertThat(converter.fromTree(converter.toTree(purchase()), purchase().getClass()))
        .isEqualTo(purchase());
    assertThatThrownBy(() -> converter.toTree(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> converter.fromTree(null, IssueTickets.class))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> converter.fromTree(jsonMapper().nullNode(), IssueTickets.class))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> converter.fromTree(jsonMapper().readTree("{\"bad\":true}"), IssueTickets.class))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
