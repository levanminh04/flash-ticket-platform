package com.flashticket.ticket_service.worker;

import com.flashticket.ticket_service.application.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "${ticket.features.business-enabled:false} and ${ticket.features.workers-enabled:false}")
public final class TicketJobs {
  private static final Logger LOG = LoggerFactory.getLogger(TicketJobs.class);
  private final TicketService service;
  private final TicketOutboxPublisher outbox;
  private final TicketDeliveryWorker delivery;
  private final int batch;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public TicketJobs(
      TicketService service,
      TicketOutboxPublisher outbox,
      TicketDeliveryWorker delivery,
      @Value("${ticket.workers.batch-size:20}") int batch) {
    if (batch < 1) throw new IllegalArgumentException("Invalid batch size");
    this.service = service;
    this.outbox = outbox;
    this.delivery = delivery;
    this.batch = batch;
  }

  @Scheduled(fixedDelayString = "${ticket.workers.poll-delay:1s}")
  public void publish() {
    safely(() -> outbox.publishBatch(batch));
  }

  @Scheduled(fixedDelayString = "${ticket.workers.poll-delay:1s}")
  public void deliver() {
    safely(() -> delivery.deliverBatch(batch));
  }

  @Scheduled(fixedDelayString = "${ticket.workers.poll-delay:1s}")
  public void cancel() {
    safely(() -> service.voidCancelled(batch));
  }

  private void safely(Runnable work) {
    try {
      work.run();
    } catch (RuntimeException failure) {
      LOG.warn("Ticket worker deferred failureType={}", failure.getClass().getSimpleName());
    }
  }
}
