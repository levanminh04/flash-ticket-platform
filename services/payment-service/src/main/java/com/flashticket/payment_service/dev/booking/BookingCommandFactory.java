package com.flashticket.payment_service.dev.booking;

import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.hashing.ContractHashService;
import java.math.BigInteger;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Local command producer; never registered as an application bean. */
public final class BookingCommandFactory {
  private final ContractHashService hashes;
  private final Clock clock;

  public BookingCommandFactory(ContractHashService hashes, Clock clock) {
    this.hashes = Objects.requireNonNull(hashes);
    this.clock = Objects.requireNonNull(clock);
  }

  public Envelope<StartPayment> create(String buyerSubject, String amount, Duration lifetime) {
    if (buyerSubject == null
        || buyerSubject.isBlank()
        || amount == null
        || !amount.matches("[1-9][0-9]{0,9}")
        || new BigInteger(amount).compareTo(new BigInteger("9999999999")) > 0
        || lifetime == null
        || lifetime.compareTo(Duration.ofSeconds(5)) < 0
        || lifetime.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException(
          "Expected buyer subject, integer VND amount, and lifetime 5s..1h");
    }
    var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    var orderId = UUID.randomUUID();
    var requestId = UUID.randomUUID();
    var item =
        new PurchaseItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Simulator ticket",
            1,
            amount,
            amount,
            null,
            List.of());
    var purchase =
        new PurchaseSnapshot(
            1,
            orderId,
            UUID.randomUUID(),
            buyerSubject,
            "simulator@example.test",
            "1",
            "1",
            now,
            now.plus(lifetime),
            "VND",
            "Booking simulator",
            now.plus(Duration.ofDays(1)),
            now.plus(Duration.ofDays(1)).plusSeconds(7200),
            null,
            PurchaseMode.QUANTITY,
            null,
            amount,
            "0",
            amount,
            null,
            List.of(item));
    var data =
        new StartPayment(
            requestId,
            UUID.randomUUID(),
            hashes.requestHash(orderId, PaymentProvider.VNPAY, buyerSubject),
            PaymentProvider.VNPAY,
            purchase);
    return new Envelope<>(
        requestId, "payment.start-requested.v1", 1, orderId, "1", now, orderId, null, null, data);
  }
}
