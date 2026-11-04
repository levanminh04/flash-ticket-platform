package com.flashticket.ticket_service.worker;

import com.flashticket.ticket_service.qr.QrUnavailableException;
import jakarta.mail.MessagingException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SMTP runs after claim commit. A lost SMTP response may cause a duplicate notification only. */
@Component
@ConditionalOnExpression(
    "${ticket.features.business-enabled:false} and ${ticket.features.workers-enabled:false}")
public final class TicketDeliveryWorker {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final JavaMailSender mail;
  private final TicketEmailComposer composer;
  private final String buyerBaseUrl, from;
  private final Duration lease;

  private record Job(UUID id, UUID order, String email, int attempt) {}

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public TicketDeliveryWorker(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      JavaMailSender mail,
      @Value("${ticket.delivery.buyer-base-url:}") String buyerBaseUrl,
      @Value("${ticket.delivery.from:}") String from,
      @Value("${ticket.delivery.claim-lease:60s}") Duration lease,
      TicketEmailComposer composer) {
    if (lease.isNegative() || lease.isZero())
      throw new IllegalArgumentException("Invalid delivery configuration");
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(manager);
    this.mail = mail;
    this.buyerBaseUrl = buyerBaseUrl;
    this.from = from;
    this.lease = lease;
    this.composer = composer;
  }

  public int deliverBatch(int batch) {
    int finished = 0;
    for (int i = 0; i < batch; i++) {
      Job job = claim();
      if (job == null) break;
      if (!eligible(job)) {
        finished += complete(job, "NOT_SENT", "EVENT_OR_ISSUANCE_UNAVAILABLE");
        continue;
      }
      String link = link(job.order());
      if (link == null || from.isBlank() || from.indexOf('\r') >= 0 || from.indexOf('\n') >= 0) {
        retry(job, "DELIVERY_CONFIGURATION_UNAVAILABLE");
        continue;
      }
      try {
        var tickets =
            jdbc.query(
                "SELECT t.id,t.display_snapshot,t.state,t.qr_token_ciphertext,t.qr_key_version FROM ticket_schema.tbl_tickets t JOIN ticket_schema.tbl_ticket_deliveries d ON d.issuance_id=t.issuance_id WHERE d.id=? ORDER BY t.order_item_id,t.ordinal",
                (r, n) ->
                    new TicketEmailComposer.StoredTicket(
                        r.getObject("id", UUID.class),
                        r.getString("display_snapshot"),
                        r.getString("state"),
                        r.getBytes("qr_token_ciphertext"),
                        r.getString("qr_key_version")),
                job.id());
        Integer expected =
            jdbc.queryForObject(
                "SELECT i.expected_ticket_count FROM ticket_schema.tbl_issuances i JOIN ticket_schema.tbl_ticket_deliveries d ON d.issuance_id=i.id WHERE d.id=?",
                Integer.class,
                job.id());
        if (expected == null || tickets.size() != expected) {
          retry(job, "DELIVERY_DATA_UNAVAILABLE");
          continue;
        }
        var message = mail.createMimeMessage();
        composer.compose(message, from, job.email(), job.order(), link, tickets);
        // Rendering/decryption can take time; recheck cancellation and lease immediately before
        // SMTP.
        if (!eligible(job)) {
          finished += complete(job, "NOT_SENT", "EVENT_OR_ISSUANCE_UNAVAILABLE");
          continue;
        }
        mail.send(message);
        // DELIVERED means SMTP accepted the notice, not that a buyer read it. An in-flight cancel
        // cannot unsend it.
        finished += complete(job, "DELIVERED", null);
      } catch (org.springframework.mail.MailException unavailable) {
        retry(job, "SMTP_UNAVAILABLE");
      } catch (QrUnavailableException unavailable) {
        retry(job, "QR_UNAVAILABLE");
      } catch (MessagingException | RuntimeException unavailable) {
        retry(job, "DELIVERY_CONTENT_UNAVAILABLE");
      }
    }
    return finished;
  }

  private Job claim() {
    return transactions.execute(
        status -> {
          var jobs =
              jdbc.query(
                  "SELECT d.id,d.recipient_email,d.attempts,i.order_id FROM ticket_schema.tbl_ticket_deliveries d JOIN ticket_schema.tbl_issuances i ON i.id=d.issuance_id WHERE d.finished_at IS NULL AND d.next_attempt_at<=clock_timestamp() ORDER BY d.requested_at,d.id FOR UPDATE OF d SKIP LOCKED LIMIT 1",
                  (r, n) ->
                      new Job(
                          r.getObject("id", UUID.class),
                          r.getObject("order_id", UUID.class),
                          r.getString("recipient_email"),
                          Math.addExact(r.getInt("attempts"), 1)));
          if (jobs.isEmpty()) return null;
          Job job = jobs.getFirst();
          jdbc.update(
              "UPDATE ticket_schema.tbl_ticket_deliveries SET attempts=?,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond') WHERE id=?",
              job.attempt(),
              lease.toMillis(),
              job.id());
          return job;
        });
  }

  private boolean eligible(Job job) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM ticket_schema.tbl_ticket_deliveries d JOIN ticket_schema.tbl_issuances i ON i.id=d.issuance_id JOIN ticket_schema.tbl_event_access_snapshots e ON e.event_id=i.event_id WHERE d.id=? AND d.attempts=? AND d.finished_at IS NULL AND i.state='COMPLETED' AND e.event_status<>'CANCELLED'",
            Integer.class,
            job.id(),
            job.attempt());
    return count != null && count == 1;
  }

  private int complete(Job job, String outcome, String failure) {
    return jdbc.update(
        "UPDATE ticket_schema.tbl_ticket_deliveries SET finished_at=clock_timestamp(),outcome=?,failure_code=? WHERE id=? AND attempts=? AND finished_at IS NULL",
        outcome,
        failure,
        job.id(),
        job.attempt());
  }

  private void retry(Job job, String code) {
    jdbc.update(
        "UPDATE ticket_schema.tbl_ticket_deliveries SET failure_code=?,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond') WHERE id=? AND attempts=? AND finished_at IS NULL",
        code,
        TicketOutboxPublisher.retryMillis(job.attempt()),
        job.id(),
        job.attempt());
  }

  private String link(UUID order) {
    if (buyerBaseUrl.isEmpty()) return "";
    try {
      URI base = URI.create(buyerBaseUrl);
      if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
          || base.getHost() == null
          || base.getRawUserInfo() != null
          || base.getRawQuery() != null
          || base.getRawFragment() != null) return null;
      return buyerBaseUrl.replaceAll("/+$", "") + "/orders/" + order + "/tickets";
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }
}
