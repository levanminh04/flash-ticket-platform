package com.flashticket.booking_service.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class BookingRoutingDataSource extends AbstractRoutingDataSource {
  @Override
  protected Object determineCurrentLookupKey() {
    // Chỉ loader đã qua gate mới đặt marker; read-only thông thường vẫn đọc writer.
    String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        && (BookingDataSourceConfig.ADMIN_CHART_TRANSACTION.equals(transactionName)
            || BookingDataSourceConfig.SALES_REPORT_TRANSACTION.equals(transactionName))
        && getResolvedDataSources().containsKey("reader")) {
      return "reader";
    }
    return "writer";
  }
}
