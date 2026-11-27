package com.flashticket.ticket_service.application;

import com.flashticket.ticket_service.config.TicketListReplicaHealth;
import com.flashticket.ticket_service.web.TicketOperations;
import com.flashticket.ticket_service.web.TicketViews;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public class TicketReadService {
  private final TicketOperations operations;
  private final ObjectProvider<TransactionTemplate> transactionProvider;
  private final ObjectProvider<TicketListReplicaHealth> healthProvider;
  private final Counter writerReads;
  private final Counter readerReads;
  private final Counter readerErrors;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed metrics without exposing them.")
  public TicketReadService(
      TicketOperations operations,
      @Qualifier("ticketListReadTransactionTemplate")
          ObjectProvider<TransactionTemplate> transactionProvider,
      @Qualifier("ticketListReplicaHealth") ObjectProvider<TicketListReplicaHealth> healthProvider,
      MeterRegistry metrics) {
    this.operations = operations;
    this.transactionProvider = transactionProvider;
    this.healthProvider = healthProvider;
    this.writerReads = metrics.counter("ticket.list.source", "source", "writer");
    this.readerReads = metrics.counter("ticket.list.source", "source", "reader");
    this.readerErrors = metrics.counter("ticket.list.replica.attempt.error");
  }

  public TicketViews.TicketList listTickets(UUID orderId, String actor) {
    // Giữ transaction caller; không gắn nhãn writer khi chưa biết nguồn vật lý của nó.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isSynchronizationActive()) {
      return operations.listTickets(orderId, actor);
    }
    var transaction = transactionProvider.getIfAvailable();
    var health = healthProvider.getIfAvailable();
    // Admission trước transaction: thiếu bean/mẫu hoặc slot bận thì chưa thử reader.
    if (transaction == null || health == null || !health.tryBeginRead()) {
      writerReads.increment();
      return operations.listTickets(orderId, actor);
    }
    try {
      readerReads.increment();
      return transaction.execute(status -> operations.listTickets(orderId, actor));
    } catch (RuntimeException failure) {
      health.invalidate();
      readerErrors.increment();
      // Đã bắt đầu reader attempt: lỗi begin/query/commit đều đi ra, không gọi lại writer.
      throw failure;
    } finally {
      // execute hoàn tất commit/rollback rồi mới nhả slot cho request tiếp theo.
      health.finishRead();
    }
  }
}
