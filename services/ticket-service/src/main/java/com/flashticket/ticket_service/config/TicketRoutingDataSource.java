package com.flashticket.ticket_service.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class TicketRoutingDataSource extends AbstractRoutingDataSource {
  @Override
  protected Object determineCurrentLookupKey() {
    // readOnly không đủ: chỉ transaction có marker của list đã qua admission mới đọc phụ.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        && TicketDataSourceConfig.LIST_TRANSACTION.equals(
            TransactionSynchronizationManager.getCurrentTransactionName())
        && getResolvedDataSources().containsKey("reader")) {
      return "reader";
    }
    return "writer";
  }
}
