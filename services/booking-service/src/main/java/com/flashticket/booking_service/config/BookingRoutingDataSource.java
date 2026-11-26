package com.flashticket.booking_service.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class BookingRoutingDataSource extends AbstractRoutingDataSource {
  @Override
  protected Object determineCurrentLookupKey() {
    // Chi tiết đơn, tồn ghế và báo cáo Organizer cũng read-only nhưng phải đọc writer.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        && BookingDataSourceConfig.ADMIN_CHART_TRANSACTION.equals(
            TransactionSynchronizationManager.getCurrentTransactionName())
        && getResolvedDataSources().containsKey("reader")) {
      return "reader";
    }
    return "writer";
  }
}
