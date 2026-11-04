package com.flashticket.ticket_service.contract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Wire contract for the existing Payment-coordinated Saga; Ticket is a participant. */
@Component
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public final class TicketSagaMessages {
  public static final String RESULT = "ticket.issuance-result.v1";
  private final String completedType, completedRoutingKey, failedRoutingKey;

  public TicketSagaMessages(
      @Value("${ticket.messaging.completed-result-message-type:ticket.issued.v1}")
          String completedType,
      @Value("${ticket.messaging.completed-result-routing-key:ticket.issued.v1}")
          String completedRoutingKey,
      @Value("${ticket.messaging.failed-result-routing-key:ticket.issuance-failed.v1}")
          String failedRoutingKey) {
    if (!("ticket.issued.v1".equals(completedType) || RESULT.equals(completedType))
        || completedRoutingKey == null
        || completedRoutingKey.isBlank()
        || failedRoutingKey == null
        || failedRoutingKey.isBlank())
      throw new IllegalArgumentException("Invalid Saga result configuration");
    this.completedType = completedType;
    this.completedRoutingKey = completedRoutingKey;
    this.failedRoutingKey = failedRoutingKey;
  }

  public String messageType(String state) {
    return switch (state) {
      case "COMPLETED" -> completedType;
      case "FAILED" -> RESULT;
      default -> throw new IllegalArgumentException("Nonterminal issuance has no Saga result");
    };
  }

  public String routingKey(String storedType, String state) {
    // Never silently rewrite a previously committed message type after a configuration change.
    if (!messageType(state).equals(storedType))
      throw new IllegalArgumentException("Stored result does not match configured Saga contract");
    return "COMPLETED".equals(state) ? completedRoutingKey : failedRoutingKey;
  }
}
