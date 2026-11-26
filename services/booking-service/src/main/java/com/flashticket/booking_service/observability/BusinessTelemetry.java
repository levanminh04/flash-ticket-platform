package com.flashticket.booking_service.observability;

import java.util.*;
import org.slf4j.*;
import org.springframework.transaction.support.*;

/** Captures immutable decision values; success is emitted only after the transaction commits. */
public final class BusinessTelemetry {
  private static final Logger LOG = LoggerFactory.getLogger(BusinessTelemetry.class);

  private BusinessTelemetry() {}

  public static void committed(
      String operation, UUID order, Object before, Object after, String reason) {
    Map<String, Object> fields = new LinkedHashMap<>();
    if (order != null) {
      fields.put("orderId", order.toString());
      fields.put("correlationId", order.toString());
    }
    committed(operation, fields, before, after, reason);
  }

  public static void committed(
      String operation, Map<String, Object> fields, Object before, Object after, String reason) {
    Map<String, Object> snapshot = new LinkedHashMap<>(fields);
    if (before != null) snapshot.put("stateBefore", before.toString());
    if (after != null) snapshot.put("stateAfter", after.toString());
    String outcome =
        Objects.toString(
            snapshot.remove("outcome"),
            Set.of("FAILED", "ISSUANCE_FAILED").contains(Objects.toString(after, ""))
                ? "FAILED"
                : "SUCCESS");
    Map<String, String> context = MDC.getCopyOfContextMap();
    Runnable emit =
        () -> {
          var previous = MDC.getCopyOfContextMap();
          try {
            if (context == null) MDC.clear();
            else MDC.setContextMap(context);
            var log =
                LOG.atInfo()
                    .addKeyValue("eventName", "booking." + operation)
                    .addKeyValue("operation", operation)
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("reasonCode", reason);
            snapshot.forEach(log::addKeyValue);
            log.log("Business decision committed");
          } finally {
            if (previous == null) MDC.clear();
            else MDC.setContextMap(previous);
          }
        };
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              emit.run();
            }
          });
    else emit.run();
  }
}
