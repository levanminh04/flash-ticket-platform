package com.flashticket.booking_service.mapper;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.snapshot.SelectionSnapshot;
import com.flashticket.booking_service.entity.*;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.*;
import com.flashticket.booking_service.enums.Currency;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

class MapperTests {
  static AnnotationConfigApplicationContext context;
  static MappingValueConverter converter;
  static OrderMapper orders;
  static OutboxMessageMapper outbox;

  @BeforeAll
  static void setup() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(JsonMapper.class, () -> jsonMapper());
    context.register(
        MappingValueConverter.class,
        LocalValidatorFactoryBean.class,
        OrderMapperImpl.class,
        OutboxMessageMapperImpl.class);
    context.refresh();
    converter = context.getBean(MappingValueConverter.class);
    orders = context.getBean(OrderMapper.class);
    outbox = context.getBean(OutboxMessageMapper.class);
  }

  @AfterAll
  static void close() {
    context.close();
  }

  @ParameterizedTest
  @CsvSource({"0,0", "100.00,100", "1E+3,1000", "9999999999999999999,9999999999999999999"})
  void convertsMoneyWithoutPrecisionLoss(String input, String expected) {
    assertThat(converter.toMoney(new BigDecimal(input))).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "1.01", "10000000000000000000"})
  void rejectsInvalidMoney(String input) {
    assertThatThrownBy(() -> converter.toMoney(new BigDecimal(input)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullMoneyAndInvalidVersion() {
    assertThatThrownBy(() -> converter.toMoney(null)).isInstanceOf(IllegalArgumentException.class);
    for (Long version : Arrays.asList(null, 0L, -1L)) {
      assertThatThrownBy(() -> converter.toUInt64(version))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(converter.toUInt64(Long.MAX_VALUE)).isEqualTo("9223372036854775807");
  }

  @Test
  void selectionConversionReturnsAnImmutableCopy() {
    var seats = new ArrayList<>(List.of(seat()));
    var result = converter.seats(new SelectionSnapshot(1, seats));
    seats.clear();
    assertThat(result).containsExactly(seat());
    assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThat(converter.seats(null)).isEmpty();
  }

  @Test
  void typedJsonRoundTripRejectsMissingAndMalformedPayload() {
    var tree = converter.toTree(purchase());
    assertThat(converter.fromTree(tree, purchase().getClass())).isEqualTo(purchase());
    assertThatThrownBy(() -> converter.toTree(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> converter.fromTree(null, Object.class))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> converter.fromTree(jsonMapper().nullNode(), Object.class))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> converter.fromTree(jsonMapper().readTree("{\"bad\":1}"), purchase().getClass()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mapsOrderFieldsAndNestedSelections() {
    Order order = new Order();
    order.setId(ORDER);
    order.setEventId(EVENT);
    order.setLifecycleState(OrderState.PENDING_PAYMENT);
    order.setCustomerEmail("buyer@example.com");
    order.setCurrency(Currency.VND);
    order.setSubtotal(new BigDecimal("200"));
    order.setDiscount(BigDecimal.ZERO);
    order.setTotal(new BigDecimal("200"));
    order.setSourceConfigVersion(Long.MAX_VALUE);
    order.setExpiresAt(NOW.plusSeconds(600));
    order.setPaymentFrozenAt(NOW);
    OrderItem item = new OrderItem();
    item.setId(MESSAGE);
    item.setTicketTypeId(TICKET);
    item.setTicketTypeName("Standard");
    item.setQuantity(2);
    item.setUnitPrice(new BigDecimal("100"));
    item.setLineSubtotal(new BigDecimal("200"));
    item.setSectorId(SECTOR);
    item.setSelectionSnapshot(new SelectionSnapshot(1, List.of(seat())));
    order.addItem(item);
    var view = orders.toView(order);
    assertThat(view.getOrderId()).isEqualTo(ORDER);
    assertThat(view.getEventId()).isEqualTo(EVENT);
    assertThat(view.getState()).isEqualTo(OrderState.PENDING_PAYMENT);
    assertThat(view.getSubtotal()).isEqualTo("200");
    assertThat(view.getDiscount()).isEqualTo("0");
    assertThat(view.getTotal()).isEqualTo("200");
    assertThat(view.getSourceConfigVersion()).isEqualTo("9223372036854775807");
    assertThat(view.getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    assertThat(view.getPaymentFrozenAt()).isEqualTo(NOW);
    var line = view.getItems().getFirst();
    assertThat(line.getOrderItemId()).isEqualTo(MESSAGE);
    assertThat(line.getUnitPrice()).isEqualTo("100");
    assertThat(line.getLineSubtotal()).isEqualTo("200");
    assertThat(line.getSeats()).containsExactly(seat());
    assertThat(orders.toView(null)).isNull();
    assertThat(orders.toItemView(null)).isNull();
  }

  @Test
  void allEightOutboxContractsRoundTripWithMetadataAndDefaults() {
    payloads()
        .forEach(
            (type, payload) -> {
              Envelope<?> envelope = envelope(type, payload);
              var row = outbox.toEntity(envelope);
              assertThat(row.getMessageId()).isEqualTo(MESSAGE);
              assertThat(row.getAggregateId()).isEqualTo(ORDER);
              assertThat(row.getAggregateVersion()).isEqualTo(2L);
              assertThat(row.getMessageType()).isEqualTo(type);
              assertThat(row.getAttempts()).isZero();
              assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
              assertThat(row.getPublishedAt()).isNull();
              assertThat(row.getLastErrorCode()).isNull();
              var restored = outbox.toEnvelope(row, payload.getClass());
              assertThat(restored).isEqualTo(envelope);
            });
  }

  @Test
  void invalidEnvelopesAreRejectedBeforeMapping() {
    assertThatThrownBy(() -> outbox.toEntity(null)).isInstanceOf(IllegalArgumentException.class);
    var valid =
        envelope("booking.order-completed.v1", payloads().get("booking.order-completed.v1"));
    for (String field : List.of("data", "aggregateVersion", "messageType", "correlationId")) {
      assertThatThrownBy(() -> outbox.toEntity(with(valid, field, null)))
          .isInstanceOf(ConstraintViolationException.class);
    }
    assertThatThrownBy(
            () -> outbox.toEntity(with(valid, "aggregateVersion", "9223372036854775808")))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void invalidRowsAndTypeRequestsAreRejected() {
    assertThatThrownBy(() -> outbox.toEnvelope(null, Object.class))
        .isInstanceOf(IllegalArgumentException.class);
    var row = outbox.toEntity(envelope("event.sales-configured.v1", sales()));
    assertThatThrownBy(() -> outbox.toEnvelope(row, purchase().getClass()))
        .isInstanceOf(IllegalArgumentException.class);
    row.setSchemaVersion(2);
    assertThatThrownBy(() -> outbox.toEnvelope(row, sales().getClass()))
        .isInstanceOf(IllegalArgumentException.class);
    row.setSchemaVersion(1);
    row.setAggregateVersion(0L);
    assertThatThrownBy(() -> outbox.toEnvelope(row, sales().getClass()))
        .isInstanceOf(IllegalArgumentException.class);
    row.setAggregateVersion(1L);
    row.setPayload(null);
    assertThatThrownBy(() -> outbox.toEnvelope(row, sales().getClass()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
