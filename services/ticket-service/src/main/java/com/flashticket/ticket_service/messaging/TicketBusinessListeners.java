package com.flashticket.ticket_service.messaging;

import com.flashticket.ticket_service.application.TicketService;
import com.flashticket.ticket_service.contract.TicketContracts;
import com.flashticket.ticket_service.contract.TicketContracts.AccessConfiguration;
import com.flashticket.ticket_service.contract.TicketContracts.Envelope;
import com.flashticket.ticket_service.contract.TicketContracts.EventCancellation;
import com.flashticket.ticket_service.contract.TicketContracts.InvalidContractException;
import com.flashticket.ticket_service.contract.TicketContracts.IssueTickets;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Raw-message boundary: the business transaction returns only after commit, then ACK is sent. */
@Component
@ConditionalOnExpression(
    "${ticket.features.business-enabled:false} and ${ticket.features.listeners-enabled:false}")
public class TicketBusinessListeners {
  private static final Logger LOG = LoggerFactory.getLogger(TicketBusinessListeners.class);
  private final JsonMapper json;
  private final TicketService tickets;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public TicketBusinessListeners(JsonMapper json, TicketService tickets) {
    this.json = json;
    this.tickets = tickets;
  }

  @RabbitListener(
      queues = "${ticket.messaging.access-configured-queue}",
      ackMode = "MANUAL",
      messageConverter = "ticketRawMessageConverter")
  public void access(Message message, Channel channel) throws IOException {
    consume(message, channel, AccessConfiguration.class, tickets::access);
  }

  @RabbitListener(
      queues = "${ticket.messaging.event-cancelled-queue}",
      ackMode = "MANUAL",
      messageConverter = "ticketRawMessageConverter")
  public void cancellation(Message message, Channel channel) throws IOException {
    consume(message, channel, EventCancellation.class, tickets::cancel);
  }

  @RabbitListener(
      queues = "${ticket.messaging.issuance-queue}",
      ackMode = "MANUAL",
      messageConverter = "ticketRawMessageConverter")
  public void issue(Message message, Channel channel) throws IOException {
    consume(message, channel, IssueTickets.class, tickets::issue);
  }

  private <T> void consume(
      Message message, Channel channel, Class<T> dataType, Consumer<Envelope<T>> transaction)
      throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    try {
      transaction.accept(TicketContracts.decode(json, message.getBody(), dataType));
    } catch (InvalidContractException ex) {
      // Fixed diagnostic code: payload, broker headers and exception text stay inside the boundary.
      LOG.warn("Ticket business message rejected; code=INVALID_CONTRACT");
      channel.basicReject(tag, false);
      return;
    } catch (RuntimeException ex) {
      LOG.warn("Ticket business message deferred; failureType={}", ex.getClass().getSimpleName());
      channel.basicNack(tag, false, true);
      return;
    }
    // Keep ACK outside the business catch: a transport failure after commit must not send a NACK.
    channel.basicAck(tag, false);
  }
}
