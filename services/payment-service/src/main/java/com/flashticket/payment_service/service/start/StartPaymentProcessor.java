package com.flashticket.payment_service.service.start;

import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.validation.MessageInvariantValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

@Service
@Profile("payment-simulator & !prod & !production")
@RequiredArgsConstructor
public class StartPaymentProcessor {
  private final ContractCodec codec;
  private final MessageInvariantValidator validator;
  private final ContractHashService hashes;
  private final StartPaymentStore store;
  private final BookingPaymentGuard booking;

  public StartPaymentResult handle(String json) {
    var command = codec.read(json, new TypeReference<Envelope<StartPayment>>() {});
    validator.validateStartPayment(command);
    var hash = hashes.envelopeHash(command);
    var replay = store.replay(command.messageId(), hash);
    if (replay != null) return replay;
    // No transaction/row lock is held while the future real Booking client runs.
    var rejection = booking.rejection(command.data().purchase());
    return store.apply(command, hash, hashes.purchaseHash(command.data().purchase()), rejection);
  }
}
