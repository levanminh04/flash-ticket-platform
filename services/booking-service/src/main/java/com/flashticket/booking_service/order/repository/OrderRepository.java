package com.flashticket.booking_service.order.repository;

import com.flashticket.booking_service.entity.Order;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  // Lấy giờ thực sau khi chờ khóa để kiểm tra thời hạn bán vé.
  @Query(value = "select clock_timestamp()", nativeQuery = true)
  Instant currentDatabaseTime();

  // Chỉ giới hạn chờ khóa trong transaction tạo đơn hiện tại.
  @Query(value = "select set_config('lock_timeout', :timeout, true)", nativeQuery = true)
  String configureTransactionLockTimeout(@Param("timeout") String timeout);
}
