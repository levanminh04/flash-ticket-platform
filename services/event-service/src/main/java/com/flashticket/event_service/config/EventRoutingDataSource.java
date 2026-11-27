package com.flashticket.event_service.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class EventRoutingDataSource extends AbstractRoutingDataSource {
  @Override
  protected Object determineCurrentLookupKey() {
    // readOnly thường vẫn writer; chỉ facade qua gate mới mở transaction có marker này.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        && EventDataSourceConfig.REPLICA_READ_TRANSACTION.equals(
            TransactionSynchronizationManager.getCurrentTransactionName())
        && getResolvedDataSources().containsKey("reader")) {
      return "reader";
    }
    return "writer";
  }
}
