package com.flashticket.payment_service.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class PaymentRoutingDataSource extends AbstractRoutingDataSource {
  @Override
  protected Object determineCurrentLookupKey() {
    // readOnly mặc định của repository/worker không phải quyền đọc standby.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        && PaymentDataSourceConfig.REPORT_TRANSACTION.equals(
            TransactionSynchronizationManager.getCurrentTransactionName())
        && getResolvedDataSources().containsKey("reader")) return "reader";
    return "writer";
  }
}
