package com.flashticket.booking_service.order.service;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.availability.repository.SectorInventoryRepository;
import com.flashticket.booking_service.dto.snapshot.SelectionSnapshot;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.entity.OrderItem;
import com.flashticket.booking_service.entity.SectorInventory;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.enums.SalesMode;
import com.flashticket.booking_service.enums.SectorType;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.mapper.MappingValueConverter;
import com.flashticket.booking_service.mapper.OrderMapper;
import com.flashticket.booking_service.mapper.OrderMapperImpl;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class OrderViewAssemblerTests {
  static AnnotationConfigApplicationContext context;
  static OrderMapper mapper;
  SectorInventoryRepository sectors;
  OrderViewAssembler assembler;

  @BeforeAll
  static void openContext() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(JsonMapper.class, () -> jsonMapper());
    context.register(MappingValueConverter.class, OrderMapperImpl.class);
    context.refresh();
    mapper = context.getBean(OrderMapper.class);
  }

  @AfterAll
  static void closeContext() {
    context.close();
  }

  @BeforeEach
  void setup() {
    sectors = mock(SectorInventoryRepository.class);
    assembler = new OrderViewAssembler(mapper, sectors);
  }

  private Order order(SalesMode mode, UUID sectorId, SelectionSnapshot selection) {
    var order = new Order();
    order.setId(UUID.randomUUID());
    order.setEventId(EVENT);
    order.setLifecycleState(OrderState.COMPLETED);
    order.setSubtotal(BigDecimal.ONE);
    order.setDiscount(BigDecimal.ZERO);
    order.setTotal(BigDecimal.ONE);
    order.setSourceConfigVersion(1L);
    var item = new OrderItem();
    item.setId(UUID.randomUUID());
    item.setEventId(EVENT);
    item.setSectorId(sectorId);
    item.setSalesMode(mode);
    item.setQuantity(1);
    item.setUnitPrice(BigDecimal.ONE);
    item.setLineSubtotal(BigDecimal.ONE);
    item.setSelectionSnapshot(selection);
    order.addItem(item);
    return order;
  }

  private SectorInventory sector(UUID id, UUID eventId, SectorType kind) {
    var sector = new SectorInventory();
    sector.setSectorId(id);
    sector.setEventId(eventId);
    sector.setSectorType(kind);
    return sector;
  }

  @Test
  void batchesDistinctRequiredSectorsOnceAndPreservesOrder() {
    var secondSector = UUID.randomUUID();
    var populatedSector = UUID.randomUUID();
    var first = order(SalesMode.SEAT_MAP, SECTOR, new SelectionSnapshot(1, null));
    var second = order(SalesMode.SEAT_MAP, secondSector, null);
    var repeated = order(SalesMode.SEAT_MAP, SECTOR, new SelectionSnapshot(1, List.of()));
    var quantity = order(SalesMode.QUANTITY, UUID.randomUUID(), null);
    var seated =
        order(SalesMode.SEAT_MAP, populatedSector, new SelectionSnapshot(1, List.of(seat())));
    when(sectors.findAllById(any()))
        .thenReturn(
            List.of(
                sector(secondSector, EVENT, SectorType.STANDING),
                sector(SECTOR, EVENT, SectorType.STANDING)));

    var views =
        assembler.toViews(
            List.of(first, second, repeated, quantity, seated),
            Map.of(EVENT, "Đêm nhạc Việt – 東京"));
    assertThat(views)
        .allSatisfy(view -> assertThat(view.getEventTitle()).isEqualTo("Đêm nhạc Việt – 東京"));
    assertThat(views)
        .extracting(view -> view.getOrderId())
        .containsExactly(
            first.getId(), second.getId(), repeated.getId(), quantity.getId(), seated.getId());
    assertThat(views.subList(0, 4))
        .allSatisfy(view -> assertThat(view.getItems().getFirst().getSeats()).isEmpty());
    assertThat(views.getLast().getItems().getFirst().getSeats()).containsExactly(seat());
    assertThatThrownBy(() -> views.getLast().getItems().getFirst().getSeats().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<UUID>> ids = ArgumentCaptor.forClass(Iterable.class);
    verify(sectors).findAllById(ids.capture());
    assertThat(ids.getValue()).containsExactlyInAnyOrder(SECTOR, secondSector);
    verifyNoMoreInteractions(sectors);
  }

  @Test
  void skipsLookupForQuantityAndPopulatedSeats() {
    assertThat(
            assembler.toViews(
                List.of(
                    order(SalesMode.QUANTITY, null, null),
                    order(SalesMode.QUANTITY, null, new SelectionSnapshot(1, List.of())),
                    order(SalesMode.SEAT_MAP, SECTOR, new SelectionSnapshot(1, List.of(seat()))))))
        .hasSize(3);
    var quantity = order(SalesMode.QUANTITY, null, null);
    assertThat(assembler.toViews(List.of(quantity), Map.of()).getFirst().getEventTitle()).isNull();
    assertThat(assembler.toView(quantity).getEventTitle()).isNull();
    verifyNoInteractions(sectors);
  }

  @Test
  void emptyAndNullSourcesDoNotQuery() {
    assertThat(assembler.toViews(List.of())).isEmpty();
    assertThat(assembler.toView(null)).isNull();
    assertThat(assembler.toViews(Arrays.asList((Order) null)))
        .containsExactly((com.flashticket.booking_service.dto.response.OrderView) null);
    verifyNoInteractions(sectors);
  }

  @Test
  void singleOrderUsesBatchAndDoesNotRetainContextAcrossCalls() {
    var order = order(SalesMode.SEAT_MAP, SECTOR, null);
    when(sectors.findAllById(any()))
        .thenReturn(List.of(sector(SECTOR, EVENT, SectorType.STANDING)), List.of());
    assertThat(assembler.toView(order).getItems().getFirst().getSeats()).isEmpty();
    assertThatThrownBy(() -> assembler.toView(order))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
    verify(sectors, times(2)).findAllById(Set.of(SECTOR));
    verifyNoMoreInteractions(sectors);
  }

  @Test
  void rejectsSeatedUnknownAndWrongEventSectors() {
    var order = order(SalesMode.SEAT_MAP, SECTOR, new SelectionSnapshot(1, List.of()));
    when(sectors.findAllById(any()))
        .thenReturn(
            List.of(sector(SECTOR, EVENT, SectorType.SEATED)),
            List.of(),
            List.of(sector(SECTOR, UUID.randomUUID(), SectorType.STANDING)));
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> assembler.toView(order))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
    }
    verify(sectors, times(3)).findAllById(Set.of(SECTOR));
    verifyNoMoreInteractions(sectors);
  }

  @Test
  void missingIdentifiersAndNullSeatEntriesFailWithoutLookup() {
    var noSector = order(SalesMode.SEAT_MAP, null, null);
    var noEvent = order(SalesMode.SEAT_MAP, SECTOR, null);
    noEvent.getItems().getFirst().setEventId(null);
    var malformed =
        order(SalesMode.SEAT_MAP, SECTOR, new SelectionSnapshot(1, Arrays.asList(seat(), null)));
    for (var order : List.of(noSector, noEvent, malformed)) {
      assertThatThrownBy(() -> assembler.toView(order))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
    }
    verifyNoInteractions(sectors);
  }
}
