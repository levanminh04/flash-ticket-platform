package com.flashticket.event_service.observability;

import com.flashticket.event_service.domain.EventData;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class EventAudit {
  public void committed(String operation, String before, EventData after, String actor) {
    String correlation = EventContext.correlation(after.id()).toString();
    Runnable log =
        () ->
            LoggerFactory.getLogger(EventAudit.class)
                .atInfo()
                .addKeyValue("eventName", "event." + operation)
                .addKeyValue("operation", operation)
                .addKeyValue("outcome", "SUCCESS")
                .addKeyValue("correlationId", correlation)
                .addKeyValue("eventId", after.id().toString())
                .addKeyValue("actorSubject", actor)
                .addKeyValue("stateBefore", before)
                .addKeyValue("stateAfter", after.status())
                .addKeyValue("configVersion", Long.toString(after.configVersion()))
                .addKeyValue(
                    "reasonCode",
                    "EVENT_" + operation.toUpperCase(java.util.Locale.ROOT).replace('-', '_'))
                .log("Event transaction committed");
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              log.run();
            }
          });
    else log.run();
  }
}
