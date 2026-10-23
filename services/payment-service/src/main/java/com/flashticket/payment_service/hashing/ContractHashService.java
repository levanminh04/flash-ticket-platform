package com.flashticket.payment_service.hashing;
import com.flashticket.payment_service.validation.PurchaseSnapshotValidator;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.dto.message.PaymentProvider;
import java.util.UUID;
import java.util.Objects;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public final class ContractHashService {
  private final CanonicalContractSerializer serializer;
  private final PurchaseSnapshotValidator validator;
  public ContractHashService(CanonicalContractSerializer serializer, PurchaseSnapshotValidator validator) {
    this.serializer = Objects.requireNonNull(serializer);
    this.validator = Objects.requireNonNull(validator);
  }
  public String purchaseHash(PurchaseSnapshot purchase) {
    validator.validate(purchase);
    return sha256(serializer.purchaseBytes(purchase));
  }
  public String requestHash(UUID orderId, PaymentProvider provider, String actor) {
    return sha256(serializer.requestBytes(orderId,provider,actor));
  }
  /** The caller must first validate the typed envelope and its message-specific invariants. */
  public String envelopeHash(Envelope<?> validatedEnvelope) {
    return sha256(serializer.envelopeBytes(validatedEnvelope));
  }
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("JVM does not provide SHA-256",ex);
    }
  }
}
