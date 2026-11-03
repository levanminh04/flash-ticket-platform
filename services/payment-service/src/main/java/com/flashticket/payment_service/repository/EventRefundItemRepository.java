package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.EventRefundItem;
import com.flashticket.payment_service.entity.enums.EventRefundItemState;
import com.flashticket.payment_service.entity.id.EventRefundItemId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRefundItemRepository
    extends JpaRepository<EventRefundItem, EventRefundItemId> {
  Optional<EventRefundItem> findFirstByRefundIdOrderByUpdatedAtDesc(UUID refundId);

  long countByIdRunIdAndState(UUID runId, EventRefundItemState state);

  boolean existsByIdRunIdAndStateNot(UUID runId, EventRefundItemState state);
}
