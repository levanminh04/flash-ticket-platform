package com.flashticket.payment_service.client;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver.principal;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.request.PreparePurchaseSnapshotRequest;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class BookingPurchaseClient {
  private final RestClient bookingRestClient;
  private final ContractCodec codec;

  @Value("${payment.booking.snapshot-path:/internal/v1/orders/{orderId}/purchase-snapshot}")
  private String snapshotPath;

  public PurchaseSnapshot preparePurchaseSnapshot(UUID orderId, String buyerSubject) {
    try {
      String response;
      try {
        response =
            bookingRestClient
                .post()
                .uri(snapshotPath, orderId)
                .attributes(clientRegistrationId("booking"))
                .attributes(principal("payment-service"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PreparePurchaseSnapshotRequest(buyerSubject))
                .retrieve()
                .body(String.class);
      } catch (IllegalStateException ex) {
        // LoadBalancer ném IllegalStateException khi không có instance; không retry POST.
        // Chỉ phân loại trong HTTP boundary, không che lỗi trạng thái khi đọc contract.
        throw new PaymentException(PaymentErrorCode.BOOKING_UNAVAILABLE);
      }
      return codec.read(response, PurchaseSnapshot.class);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404)
        throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
      if (ex.getStatusCode().value() == 409)
        throw new PaymentException(PaymentErrorCode.PAYMENT_STATE_INVALID);
      // Không chuyển lỗi credential của service thành lỗi đăng nhập của buyer.
      throw new PaymentException(PaymentErrorCode.BOOKING_UNAVAILABLE);
    } catch (com.flashticket.payment_service.dto.common.ContractValidationException
        | RestClientException
        | org.springframework.security.oauth2.core.OAuth2AuthorizationException ex) {
      throw new PaymentException(PaymentErrorCode.BOOKING_UNAVAILABLE);
    }
  }
}
