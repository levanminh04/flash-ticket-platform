package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.dto.projection.EventTitleProjection;
import com.flashticket.booking_service.entity.EventSalesSnapshot;
import com.flashticket.booking_service.enums.EventState;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventSalesSnapshotRepository extends JpaRepository<EventSalesSnapshot, UUID> {
  @Query(
      value =
          """
          select s.event_id as "eventId",
                 s.configuration_snapshot ->> 'eventTitle' as "eventTitle"
          from booking_schema.tbl_event_sales_snapshots s
          where s.event_id in (:eventIds)
          """,
      nativeQuery = true)
  List<EventTitleProjection> findEventTitlesByEventIdIn(@Param("eventIds") List<UUID> eventIds);

  // Chỉ đọc owner để kiểm quyền trước cache, không khóa hoặc tải JSON cấu hình.
  @Query(
      "select s.organizerSubject from EventSalesSnapshot s where s.eventId = :eventId and s.eventStatus in :eventStates")
  Optional<String> findSalesOrganizerSubjectByEventId(
      @Param("eventId") UUID eventId, @Param("eventStates") List<EventState> eventStates);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EventSalesSnapshot> findByEventId(UUID eventId);

  // Nhiều đơn được đọc cùng lúc; consumer cập nhật phải chờ các đơn kết thúc.
  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<EventSalesSnapshot> findForBookingByEventId(UUID eventId);
}
