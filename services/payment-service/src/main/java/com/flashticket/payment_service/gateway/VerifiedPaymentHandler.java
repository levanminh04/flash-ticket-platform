package com.flashticket.payment_service.gateway;

/**
 * Runtime P3–P6 must implement this port. Recheck the persisted context, deduplicate complete
 * provider charge identity AND content, and commit business state/outbox before returning. An
 * attempt already succeeded does not make a different provider charge a duplicate.
 */
public interface VerifiedPaymentHandler {
  enum Outcome {
    RECORDED,
    ALREADY_RECORDED
  }

  Outcome handle(ProviderPaymentResult result);
}
