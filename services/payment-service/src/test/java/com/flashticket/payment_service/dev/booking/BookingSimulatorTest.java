package com.flashticket.payment_service.dev.booking;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.hashing.*;
import com.flashticket.payment_service.validation.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;

class BookingSimulatorTest {
  @TempDir Path directory;
  private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

  @Test
  void createsValidPayableCommandWithBuyerAndRealHashes() throws Exception {
    try (var f = new PaymentContractFixtures()) {
      var purchases = new PurchaseSnapshotValidator(f.factory.getValidator());
      var hashes =
          new ContractHashService(
              new CanonicalContractSerializer(f.factory.getValidator()), purchases);
      var invariant = new MessageInvariantValidator(purchases, hashes);
      var simulator = new BookingCommandFactory(hashes, Clock.fixed(NOW, ZoneOffset.UTC));
      var command = simulator.create("buyer-subject", "100000", Duration.ofMinutes(15));
      var decoded =
          f.codec.read(f.codec.write(command), new TypeReference<Envelope<StartPayment>>() {});
      invariant.validateStartPayment(decoded);
      assertThat(decoded.messageId()).isEqualTo(decoded.data().requestId());
      assertThat(decoded.data().purchase().buyerSubject()).isEqualTo("buyer-subject");
      assertThat(decoded.data().purchase().total()).isEqualTo("100000");
      assertThat(decoded.data().purchase().expiresAt()).isEqualTo(NOW.plusSeconds(900));
      assertThat(decoded.data().purchase().frozenAt()).isEqualTo(NOW);
      assertThat(decoded.data().requestHash()).matches("[a-f0-9]{64}");
    }
  }

  @Test
  void refusesUnpayableAmountsAndDeadlines() {
    try (var f = new PaymentContractFixtures()) {
      var hashes =
          new ContractHashService(
              new CanonicalContractSerializer(f.factory.getValidator()),
              new PurchaseSnapshotValidator(f.factory.getValidator()));
      var factory = new BookingCommandFactory(hashes, Clock.fixed(NOW, ZoneOffset.UTC));
      for (var amount : new String[] {"0", "1.5", "-1", "10000000000", "0001"}) {
        assertThatThrownBy(() -> factory.create("buyer", amount, Duration.ofMinutes(15)))
            .isInstanceOf(IllegalArgumentException.class);
      }
      assertThatThrownBy(() -> factory.create(" ", "100000", Duration.ofMinutes(15)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> factory.create("buyer", "100000", Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void replayKeepsExactEnvelopeAndRefusesOverwritingReceipt() throws Exception {
    var path = directory.resolve("command.json");
    var json = "{\"messageId\":\"original\"}\n";
    BookingCommandFile.save(path, json);
    assertThat(BookingCommandFile.read(path)).isEqualTo(json);
    assertThatThrownBy(() -> BookingCommandFile.save(path, "different"))
        .isInstanceOf(FileAlreadyExistsException.class);
    assertThat(BookingCommandFile.read(path)).isEqualTo(json);
  }
}
