package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.api.SeatMap;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.EventRepository;
import com.flashticket.event_service.persistence.SeatMapRepository;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.json.JsonMapper;

class SeatMapOptimizationTests {
  private static EventData sampleEvent(UUID id) {
    var now = Instant.parse("2026-11-21T17:00:00Z");
    return new EventData(
        id,
        "organizer-1",
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Venue A",
        "Mega Concert",
        "Desc",
        "SEAT_MAP",
        "PUBLISHED",
        now.plusSeconds(3600),
        now.plusSeconds(7200),
        now,
        now.plusSeconds(3600),
        10,
        new BigDecimal("0.05"),
        "admin",
        now,
        1L,
        1L,
        now,
        now,
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  @Test
  void eventServiceSkipsSeatMapLoadingForCompactAndIncludeSeatMapFalse() {
    var repo = mock(EventRepository.class);
    var outbox = mock(OutboxStore.class);
    var audit = mock(EventAudit.class);
    var service = new EventService(repo, outbox, audit);
    var event = sampleEvent(UUID.randomUUID());
    when(repo.list(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any(),
            anyInt(),
            eq(true)))
        .thenReturn(List.of(event));
    when(repo.withChildren(anyList(), anyBoolean())).thenReturn(List.of(event));

    var withoutMap =
        service.listPublic(null, null, null, null, null, null, false, 20, null, false, false);
    assertThat(withoutMap.items()).hasSize(1);
    assertThat(((EventViews.Public) withoutMap.items().getFirst()).seatMap()).isNull();
    verify(repo).withChildren(List.of(event), false);

    clearInvocations(repo);
    var compact =
        service.listPublic(null, null, null, null, null, null, false, 20, null, true, true);
    assertThat(compact.items()).hasSize(1);
    assertThat(compact.items().getFirst()).isInstanceOf(EventViews.PublicSearchItem.class);
    verify(repo).withChildren(List.of(event), false);

    clearInvocations(repo);
    service.listPublic(null, null, null, null, null, null, false, 20, null, false, true);
    verify(repo).withChildren(List.of(event), true);
  }

  @Test
  void eventRepositoryWithChildrenSkipsSeatMapQueryWhenDisabled() {
    var jdbc = mock(JdbcTemplate.class);
    var maps = mock(SeatMapRepository.class);
    var repo = new EventRepository(jdbc, maps);
    var event = sampleEvent(UUID.randomUUID());

    var withoutSeatMap = repo.withChildren(List.of(event), false);
    assertThat(withoutSeatMap).hasSize(1);
    assertThat(withoutSeatMap.getFirst().seatMap()).isNull();
    verify(maps, never()).load(anyList());
    verify(jdbc, times(3))
        .query(any(PreparedStatementCreator.class), any(RowCallbackHandler.class));

    when(maps.load(List.of(event.id()))).thenReturn(Map.of());
    repo.withChildren(List.of(event), true);
    verify(maps).load(List.of(event.id()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void seatMapRepositoryUsesBatchUpdateForSeatsAndHandlesNullableTicketTypeId() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var json = new JsonMapper();
    var repo = new SeatMapRepository(jdbc, json);
    var event = sampleEvent(UUID.randomUUID());
    when(jdbc.update(any(PreparedStatementCreator.class))).thenReturn(1);
    when(jdbc.batchUpdate(
            anyString(),
            any(Collection.class),
            eq(1000),
            any(ParameterizedPreparedStatementSetter.class)))
        .thenReturn(new int[][] {{1, 1}});

    var map = json.readValue(SeatMapFixtures.map(false), SeatMap.class);
    var typeId = UUID.randomUUID();
    var sector = map.sectors().getFirst();
    var firstSeat = sector.seats().getFirst();
    var secondSeat =
        new SeatMap.Seat(
            UUID.randomUUID(),
            "A2",
            "A",
            "2",
            new BigDecimal("2.50"),
            new BigDecimal("3.00"),
            typeId,
            true);
    var standingSector =
        new SeatMap.Sector(
            UUID.randomUUID(),
            "Standing",
            "GA",
            "STANDING",
            "5000",
            1,
            "#10B981",
            sector.geometry(),
            List.of());
    var seatedSector =
        new SeatMap.Sector(
            sector.sectorId(),
            sector.name(),
            sector.code(),
            "SEATED",
            "100",
            0,
            sector.colorCode(),
            sector.geometry(),
            List.of(firstSeat, secondSeat));
    var combined =
        new SeatMap(
            map.schemaVersion(),
            map.layoutId(),
            map.name(),
            map.canvasWidth(),
            map.canvasHeight(),
            map.backgroundImageReference(),
            map.layoutData(),
            List.of(seatedSector, standingSector));

    repo.save(event, combined);

    ArgumentCaptor<Collection<SeatMap.Seat>> seatsCaptor =
        ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<ParameterizedPreparedStatementSetter<SeatMap.Seat>> setterCaptor =
        ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
    verify(jdbc, times(1))
        .batchUpdate(
            contains("INSERT INTO event_schema.tbl_event_seats"),
            seatsCaptor.capture(),
            eq(1000),
            setterCaptor.capture());
    assertThat(seatsCaptor.getValue()).containsExactly(firstSeat, secondSeat);

    var psNull = mock(PreparedStatement.class);
    setterCaptor.getValue().setValues(psNull, firstSeat);
    verify(psNull).setObject(1, firstSeat.seatId());
    verify(psNull).setObject(2, event.id());
    verify(psNull).setObject(3, seatedSector.sectorId());
    verify(psNull).setNull(9, Types.OTHER);
    verify(psNull).setBoolean(10, firstSeat.isHidden());

    var psTyped = mock(PreparedStatement.class);
    setterCaptor.getValue().setValues(psTyped, secondSeat);
    verify(psTyped).setObject(9, typeId);
    verify(psTyped).setBoolean(10, true);
  }
}
