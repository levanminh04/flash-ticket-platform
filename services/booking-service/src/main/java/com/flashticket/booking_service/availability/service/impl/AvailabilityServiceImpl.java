package com.flashticket.booking_service.availability.service.impl;

import com.flashticket.booking_service.availability.repository.EventSalesSnapshotRepository;
import com.flashticket.booking_service.availability.repository.PurchaseLimitRepository;
import com.flashticket.booking_service.availability.repository.SeatInventoryRepository;
import com.flashticket.booking_service.availability.repository.SectorInventoryRepository;
import com.flashticket.booking_service.availability.repository.TicketTypeInventoryRepository;
import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.snapshot.PromotionConfigurationSnapshot;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import com.flashticket.booking_service.entity.EventSalesSnapshot;
import com.flashticket.booking_service.entity.InboxMessage;
import com.flashticket.booking_service.entity.InboxMessageId;
import com.flashticket.booking_service.entity.PromotionInventory;
import com.flashticket.booking_service.entity.SeatInventory;
import com.flashticket.booking_service.entity.SectorInventory;
import com.flashticket.booking_service.entity.TicketTypeInventory;
import com.flashticket.booking_service.enums.EventState;
import com.flashticket.booking_service.enums.SalesMode;
import com.flashticket.booking_service.enums.SeatState;
import com.flashticket.booking_service.enums.SectorType;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.messaging.repository.InboxMessageRepository;
import com.flashticket.booking_service.promotion.repository.PromotionInventoryRepository;
import jakarta.validation.Validator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {
  private final EventSalesSnapshotRepository eventSalesSnapshotRepository;
  private final TicketTypeInventoryRepository ticketTypeInventoryRepository;
  private final SectorInventoryRepository sectorInventoryRepository;
  private final SeatInventoryRepository seatInventoryRepository;
  private final PurchaseLimitRepository purchaseLimitRepository;
  private final PromotionInventoryRepository promotionInventoryRepository;
  private final InboxMessageRepository inboxMessageRepository;
  private final JsonMapper jsonMapper;
  private final Validator validator;

  @Override
  @Transactional
  public void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message) {
    if (message == null || !validator.validate(message).isEmpty())
      throw new BusinessException(ErrorCode.INVALID_MESSAGE);
    SalesConfiguration configuration = message.data();
    if (!"event.sales-configured.v1".equals(message.messageType())
        || !message.aggregateId().equals(configuration.eventId())
        || !message.aggregateVersion().equals(configuration.sourceConfigVersion()))
      throw new BusinessException(ErrorCode.INVALID_MESSAGE);

    long sourceConfigVersion = Long.parseLong(configuration.sourceConfigVersion());
    String hash = calculateConfigurationHash(configuration);
    EventSalesSnapshot snapshot =
        eventSalesSnapshotRepository.findByEventId(configuration.eventId()).orElse(null);
    var inboxId = new InboxMessageId();
    inboxId.setConsumerName("booking.sales-configured");
    inboxId.setMessageId(message.messageId());
    // Đọc receipt sau khi chờ khóa snapshot để thấy kết quả transaction trước đã commit.
    var existingInbox = inboxMessageRepository.findById(inboxId);
    if (existingInbox.isPresent()) {
      if (!hash.equals(existingInbox.get().getPayloadHash()))
        throw new BusinessException(ErrorCode.MESSAGE_ID_CONFLICT);
      return;
    }

    if (snapshot != null
        && sourceConfigVersion == snapshot.getSourceConfigVersion()
        && !hash.equals(calculateConfigurationHash(snapshot.getConfigurationSnapshot())))
      throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);

    if (snapshot == null || sourceConfigVersion > snapshot.getSourceConfigVersion()) {
      SalesConfiguration previousConfiguration =
          snapshot == null ? null : snapshot.getConfigurationSnapshot();
      if (snapshot == null) {
        snapshot = new EventSalesSnapshot();
        snapshot.setEventId(configuration.eventId());
      } else if (!snapshot.getOrganizerSubject().equals(configuration.organizerSubject())
          || snapshot.getSalesMode() != configuration.mode()
          || (snapshot.getEventStatus() == EventState.CANCELLED
              && configuration.eventStatus() != SalesConfiguration.EventStatus.CANCELLED)) {
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      }
      snapshot.setOrganizerSubject(configuration.organizerSubject());
      snapshot.setSalesMode(configuration.mode());
      snapshot.setEventStatus(EventState.valueOf(configuration.eventStatus().name()));
      snapshot.setSaleStartsAt(configuration.saleStartsAt());
      snapshot.setSaleEndsAt(configuration.saleEndsAt());
      snapshot.setPurchaseLimit(configuration.purchaseLimit());
      snapshot.setConfigurationSnapshot(configuration);
      snapshot.setSourceConfigVersion(sourceConfigVersion);
      snapshot.setReceivedAt(Instant.now());
      // @Version null ở snapshot mới buộc repository INSERT; PK chặn race lần tạo đầu.
      eventSalesSnapshotRepository.save(snapshot);
      synchronizeTicketTypeInventory(previousConfiguration, configuration, sourceConfigVersion);
      synchronizeSectorInventory(previousConfiguration, configuration, sourceConfigVersion);
      synchronizeSeatInventory(previousConfiguration, configuration, sourceConfigVersion);
      synchronizePromotionInventory(previousConfiguration, configuration, sourceConfigVersion);
    }

    // Version cũ/cùng nội dung cũng được ghi receipt, nhưng không sửa snapshot/counter.
    var inbox = new InboxMessage();
    inbox.setId(inboxId);
    inbox.setPayloadHash(hash);
    inbox.setProcessedAt(Instant.now());
    // Inbox cũng có @Version: INSERT trùng sẽ rollback, không merge đè hash đã lưu.
    inboxMessageRepository.save(inbox);
    // Commit diễn ra ở proxy trước khi trả về listener; không ACK trong transaction này.
  }

  private String calculateConfigurationHash(SalesConfiguration configuration) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(jsonMapper.writeValueAsBytes(configuration)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, exception);
    }
  }

  private void synchronizeTicketTypeInventory(
      SalesConfiguration previousConfiguration,
      SalesConfiguration configuration,
      long sourceConfigVersion) {
    if (configuration.mode() != SalesMode.QUANTITY) return;
    Set<UUID> ids = new HashSet<>();
    configuration.ticketTypes().forEach(type -> ids.add(type.ticketTypeId()));
    if (previousConfiguration != null)
      previousConfiguration.ticketTypes().forEach(type -> ids.add(type.ticketTypeId()));
    Map<UUID, TicketTypeInventory> existing = new HashMap<>();
    for (TicketTypeInventory inventory : ticketTypeInventoryRepository.findAllById(ids)) {
      if (!configuration.eventId().equals(inventory.getEventId()))
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      existing.put(inventory.getTicketTypeId(), inventory);
    }
    var inventories = new ArrayList<TicketTypeInventory>();
    for (var type : configuration.ticketTypes()) {
      TicketTypeInventory inventory = existing.get(type.ticketTypeId());
      if (inventory == null) {
        inventory = new TicketTypeInventory();
        inventory.setTicketTypeId(type.ticketTypeId());
        inventory.setEventId(configuration.eventId());
        inventory.setHeld(0L);
        inventory.setPurchased(0L);
      }
      long capacity = Long.parseLong(type.configuredQuantity());
      // Tránh tràn số khi so tổng counter; không hạ capacity dưới phần đã sử dụng.
      if (inventory.getPurchased() > capacity
          || inventory.getHeld() > capacity - inventory.getPurchased())
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      inventory.setConfiguredCapacity(capacity);
      inventory.setSourceConfigVersion(sourceConfigVersion);
      inventories.add(inventory);
    }
    // Row đã bỏ khỏi snapshot vẫn giữ nguyên để đơn cũ/release còn tham chiếu được.
    inventories.sort(Comparator.comparing(TicketTypeInventory::getTicketTypeId));
    ticketTypeInventoryRepository.saveAll(inventories);
  }

  private void synchronizeSectorInventory(
      SalesConfiguration previousConfiguration,
      SalesConfiguration configuration,
      long sourceConfigVersion) {
    if (configuration.mode() != SalesMode.SEAT_MAP) return;
    Set<UUID> ids = new HashSet<>();
    configuration.sectors().forEach(sector -> ids.add(sector.sectorId()));
    if (previousConfiguration != null)
      previousConfiguration.sectors().forEach(sector -> ids.add(sector.sectorId()));
    Map<UUID, SectorInventory> existing = new HashMap<>();
    for (SectorInventory inventory : sectorInventoryRepository.findAllById(ids)) {
      if (!configuration.eventId().equals(inventory.getEventId()))
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      existing.put(inventory.getSectorId(), inventory);
    }
    var inventories = new ArrayList<SectorInventory>();
    for (var sector : configuration.sectors()) {
      SectorInventory inventory = existing.get(sector.sectorId());
      if (inventory == null) {
        inventory = new SectorInventory();
        inventory.setSectorId(sector.sectorId());
        inventory.setEventId(configuration.eventId());
        inventory.setSectorType(sector.kind());
        inventory.setHeld(0L);
        inventory.setPurchased(0L);
      }
      // Kind gắn với ID sector và FK của ghế lịch sử; không đổi SEATED thành STANDING.
      if (inventory.getSectorType() != sector.kind())
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      long capacity = Long.parseLong(sector.configuredCapacity());
      if (inventory.getPurchased() > capacity
          || inventory.getHeld() > capacity - inventory.getPurchased())
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      inventory.setConfiguredCapacity(capacity);
      inventory.setSourceConfigVersion(sourceConfigVersion);
      inventories.add(inventory);
    }
    inventories.sort(Comparator.comparing(SectorInventory::getSectorId));
    sectorInventoryRepository.saveAll(inventories);
  }

  private void synchronizeSeatInventory(
      SalesConfiguration previousConfiguration,
      SalesConfiguration configuration,
      long sourceConfigVersion) {
    if (configuration.mode() != SalesMode.SEAT_MAP) return;
    Set<UUID> currentIds = new HashSet<>();
    configuration.seats().forEach(seat -> currentIds.add(seat.seatId()));
    Set<UUID> ids = new HashSet<>(currentIds);
    if (previousConfiguration != null)
      previousConfiguration.seats().forEach(seat -> ids.add(seat.seatId()));
    Map<UUID, SeatInventory> existing = new HashMap<>();
    for (SeatInventory inventory : seatInventoryRepository.findAllById(ids)) {
      if (!configuration.eventId().equals(inventory.getEventId()))
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      existing.put(inventory.getSeatId(), inventory);
    }
    var inventories = new ArrayList<SeatInventory>();
    for (var seat : configuration.seats()) {
      SeatInventory inventory = existing.get(seat.seatId());
      if (inventory == null) {
        inventory = new SeatInventory();
        inventory.setSeatId(seat.seatId());
        inventory.setEventId(configuration.eventId());
        inventory.setSectorType(SectorType.SEATED);
        inventory.setReservationGeneration(0L);
      }
      boolean occupied =
          inventory.getState() == SeatState.HELD || inventory.getState() == SeatState.PURCHASED;
      if (occupied) {
        // Cập nhật cấu hình không được giải phóng hay đổi loại ghế đã được giữ/bán.
        if (seat.isHidden()
            || !Objects.equals(inventory.getSectorId(), seat.sectorId())
            || !Objects.equals(inventory.getTicketTypeId(), seat.ticketTypeId()))
          throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      } else {
        inventory.setState(seat.isHidden() ? SeatState.UNAVAILABLE : SeatState.AVAILABLE);
      }
      inventory.setSectorId(seat.sectorId());
      inventory.setTicketTypeId(seat.ticketTypeId());
      inventory.setSeatLabel(seat.seatLabel());
      inventory.setIsHidden(seat.isHidden());
      inventory.setSourceConfigVersion(sourceConfigVersion);
      inventories.add(inventory);
    }
    for (SeatInventory inventory : existing.values()) {
      if (currentIds.contains(inventory.getSeatId())) continue;
      if (inventory.getState() == SeatState.HELD || inventory.getState() == SeatState.PURCHASED)
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      // Không xóa row vì lịch sử order_item_seats vẫn có thể giữ FK tới ghế này.
      inventory.setState(SeatState.UNAVAILABLE);
      inventory.setSourceConfigVersion(sourceConfigVersion);
      inventories.add(inventory);
    }
    inventories.sort(Comparator.comparing(SeatInventory::getSeatId));
    seatInventoryRepository.saveAll(inventories);
  }

  private void synchronizePromotionInventory(
      SalesConfiguration previousConfiguration,
      SalesConfiguration configuration,
      long sourceConfigVersion) {
    Set<UUID> currentIds = new HashSet<>();
    configuration.promotions().forEach(promotion -> currentIds.add(promotion.promotionId()));
    Set<UUID> ids = new HashSet<>(currentIds);
    if (previousConfiguration != null)
      previousConfiguration.promotions().forEach(promotion -> ids.add(promotion.promotionId()));
    Map<UUID, PromotionInventory> existing = new HashMap<>();
    for (PromotionInventory inventory : promotionInventoryRepository.findAllById(ids)) {
      if (!configuration.eventId().equals(inventory.getEventId()))
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      existing.put(inventory.getPromotionId(), inventory);
    }
    var inventories = new ArrayList<PromotionInventory>();
    for (var promotion : configuration.promotions()) {
      PromotionInventory inventory = existing.get(promotion.promotionId());
      if (inventory == null) {
        inventory = new PromotionInventory();
        inventory.setPromotionId(promotion.promotionId());
        inventory.setEventId(configuration.eventId());
        inventory.setHeld(0L);
        inventory.setUsed(0L);
      } else if (inventory.getHeld() > 0
          && !promotion.equals(inventory.getConfigurationSnapshot().configuration())) {
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      }
      long limit = Long.parseLong(promotion.maximumUses());
      if (inventory.getUsed() > limit || inventory.getHeld() > limit - inventory.getUsed())
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
      inventory.setConfiguredLimit(limit);
      inventory.setPerUserLimit(promotion.maximumUsesPerUser());
      inventory.setStartsAt(promotion.startsAt());
      inventory.setEndsAt(promotion.endsAt());
      inventory.setConfigurationSnapshot(new PromotionConfigurationSnapshot(1, promotion));
      inventory.setSourceConfigVersion(sourceConfigVersion);
      inventories.add(inventory);
    }
    for (PromotionInventory inventory : existing.values()) {
      if (!currentIds.contains(inventory.getPromotionId()) && inventory.getHeld() > 0)
        throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
    }
    inventories.sort(Comparator.comparing(PromotionInventory::getPromotionId));
    promotionInventoryRepository.saveAll(inventories);
  }
}
