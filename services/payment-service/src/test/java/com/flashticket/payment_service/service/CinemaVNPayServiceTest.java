package com.flashticket.payment_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.config.VNPayConfig;
import com.flashticket.payment_service.dto.request.CreatePaymentRequest;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.mapper.*;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.service.payment.PaymentService;
import com.flashticket.payment_service.util.VNPayUtil;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class CinemaVNPayServiceTest {
  static final PaymentContractFixtures F = new PaymentContractFixtures();
  final OrderPaymentSnapshotRepository snapshots = mock(OrderPaymentSnapshotRepository.class);
  final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
  final ChargeRepository charges = mock(ChargeRepository.class);
  final VNPayConfig config = mock(VNPayConfig.class);
  final ContractHashService hashes = mock(ContractHashService.class);
  final PaymentService service =
      new PaymentService(
          snapshots,
          attempts,
          mock(PaymentConfirmationRepository.class),
          mock(RefundRepository.class),
          mock(InboxMessageRepository.class),
          new PaymentViewMapperImpl(),
          new PaymentStartMapperImpl(new MoneyMapper()),
          new RefundMapperImpl(new MoneyMapper()),
          new MessageJsonMapper(F.codec),
          config,
          charges,
          hashes);
  final OrderPaymentSnapshot snapshot = PaymentReadFixtures.snapshot(null, "buyer");
  final PaymentAttempt attempt =
      PaymentAttempt.pending(
          UUID.randomUUID(),
          snapshot.getOrderId(),
          "VNPAY",
          "test",
          BigDecimal.TEN,
          "VND",
          Instant.now().minusSeconds(30),
          UUID.randomUUID(),
          "hash");

  @AfterAll
  static void close() {
    F.close();
  }

  @BeforeEach
  void setup() {
    when(config.getHashSecret()).thenReturn("fixture-secret");
    when(config.getTmnCode()).thenReturn("test");
    when(config.getVersion()).thenReturn("2.1.0");
    when(config.getCommand()).thenReturn("pay");
    when(config.getCurrencyCode()).thenReturn("VND");
    when(config.getLocale()).thenReturn("vn");
    when(config.getPaymentUrl()).thenReturn("https://sandbox.example.test/pay");
    when(config.getReturnUrl()).thenReturn("https://example.test/api/v1/payments/vnpay/return");
    when(config.getAppReturnUrl()).thenReturn("flash-ticket://payment-return");
    when(snapshots.findForUpdateByOrderId(snapshot.getOrderId())).thenReturn(Optional.of(snapshot));
    when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
    when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
    when(snapshots.findById(snapshot.getOrderId())).thenReturn(Optional.of(snapshot));
    when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));
    when(hashes.requestHash(any(), any(), any())).thenReturn("request-hash");
  }

  @Test
  void createsAndReusesPendingPaymentUsingCinemaUrlParameters() {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
    var created =
        service.createPayment(
            new CreatePaymentRequest(snapshot.getOrderId(), null), request, "buyer");
    assertThat(created.amount()).isEqualTo("10");
    assertThat(created.paymentUrl())
        .contains(
            "vnp_Amount=1000",
            "vnp_IpAddr=203.0.113.5",
            "vnp_TxnRef=" + created.attemptId(),
            "vnp_OrderType=billpayment",
            "vnp_SecureHash=");
    when(attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(snapshot.getOrderId()))
        .thenReturn(Optional.of(attempt));
    var reused =
        service.createPayment(
            new CreatePaymentRequest(snapshot.getOrderId(), "198.51.100.7"), request, "buyer");
    assertThat(reused.attemptId()).isEqualTo(attempt.getId());
    assertThat(reused.paymentUrl()).contains("vnp_IpAddr=198.51.100.7");
  }

  @Test
  void rejectsForeignExpiredAndAlreadyPaidOrders() {
    var request = new CreatePaymentRequest(snapshot.getOrderId(), null);
    assertThatThrownBy(
            () -> service.createPayment(request, new MockHttpServletRequest(), "someone-else"))
        .isInstanceOf(PaymentException.class);
    snapshot.setExpiresAt(Instant.now().minusSeconds(1));
    assertThatThrownBy(() -> service.createPayment(request, new MockHttpServletRequest(), "buyer"))
        .isInstanceOf(PaymentException.class);
    snapshot.setExpiresAt(Instant.now().plusSeconds(600));
    attempt.setState(PaymentAttemptState.SUCCEEDED);
    when(attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(snapshot.getOrderId()))
        .thenReturn(Optional.of(attempt));
    assertThatThrownBy(() -> service.createPayment(request, new MockHttpServletRequest(), "buyer"))
        .isInstanceOf(PaymentException.class);
    verify(attempts, never()).save(any());
  }

  @Test
  void recordsVerifiedSuccessAndAcknowledgesDuplicatesWithoutAnotherCharge() {
    var params = callback("00");
    assertThat(service.handleIPN(params)).contains("\"RspCode\":\"00\"");
    assertThat(attempt.getState()).isEqualTo(PaymentAttemptState.SUCCEEDED);
    assertThat(attempt.getFinishedAt()).isNotNull();
    var saved = org.mockito.ArgumentCaptor.forClass(Charge.class);
    verify(charges).save(saved.capture());
    assertThat(saved.getValue().getProviderChargeId()).isEqualTo("123456");
    assertThat(saved.getValue().getAmount()).isEqualByComparingTo("10");
    assertThat(saved.getValue().getAttemptId()).isEqualTo(attempt.getId());
    assertThat(service.handleIPN(params)).contains("\"RspCode\":\"02\"");
    verify(charges, times(1)).save(any());
  }

  @Test
  void storesFailedPaymentsWithoutCreatingACharge() {
    assertThat(service.handleIPN(callback("24"))).contains("\"RspCode\":\"00\"");
    assertThat(attempt.getState()).isEqualTo(PaymentAttemptState.FAILED);
    assertThat(attempt.getFinishedAt()).isNotNull();
    verifyNoInteractions(charges);
  }

  @ParameterizedTest
  @ValueSource(strings = {"signature", "amount", "merchant", "reference", "transaction", "date"})
  void rejectsInvalidNotificationsWithoutWritingFinancialData(String field) {
    var params = callback("00");
    switch (field) {
      case "signature" -> params.put("vnp_SecureHash", "wrong");
      case "amount" -> {
        params.put("vnp_Amount", "wrong");
        sign(params);
      }
      case "merchant" -> {
        params.put("vnp_TmnCode", "other");
        sign(params);
      }
      case "reference" -> {
        params.put("vnp_TxnRef", "invalid");
        sign(params);
      }
      case "transaction" -> {
        params.remove("vnp_TransactionNo");
        sign(params);
      }
      case "date" -> {
        params.put("vnp_PayDate", "invalid");
        sign(params);
      }
    }
    assertThat(service.handleIPN(params)).doesNotContain("\"RspCode\":\"00\"");
    assertThat(attempt.getState()).isEqualTo(PaymentAttemptState.PENDING);
    verify(charges, never()).save(any());
    verify(attempts, never()).save(any());
  }

  @Test
  void returnRedirectUsesStoredStateWithoutApplyingTheBrowserResult() {
    var params = callback("00");
    assertThat(service.buildAppReturnUri(params).toString())
        .contains("status=PENDING", "orderId=" + snapshot.getOrderId());
    assertThat(attempt.getState()).isEqualTo(PaymentAttemptState.PENDING);
    verify(attempts, never()).save(any());
    verify(charges, never()).save(any());
    params.put("vnp_SecureHash", "wrong");
    clearInvocations(attempts);
    assertThat(service.buildAppReturnUri(params).toString())
        .isEqualTo("flash-ticket://payment-return?status=FAILED");
    verifyNoInteractions(attempts);
  }

  @ParameterizedTest
  @ValueSource(strings = {"07", "01", "04", "99"})
  void keepsAmbiguousOutcomesUnfinishedUntilTheFinalIpn(String status) {
    var params = callback("07");
    params.put("vnp_TransactionStatus", status);
    sign(params);
    assertThat(service.handleIPN(params)).contains("\"RspCode\":\"00\"");
    assertThat(attempt.getState()).isEqualTo(PaymentAttemptState.UNKNOWN);
    assertThat(attempt.getFinishedAt()).isNull();
    when(attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(snapshot.getOrderId()))
        .thenReturn(Optional.of(attempt));
    assertThatThrownBy(
            () ->
                service.createPayment(
                    new CreatePaymentRequest(snapshot.getOrderId(), null),
                    new MockHttpServletRequest(),
                    "buyer"))
        .isInstanceOf(PaymentException.class);
    verify(charges, never()).save(any());
    assertThat(service.handleIPN(callback("00"))).contains("\"RspCode\":\"00\"");
    assertThat(attempt.getState()).isEqualTo(PaymentAttemptState.SUCCEEDED);
    assertThat(attempt.getFinishedAt()).isNotNull();
    verify(charges, times(1)).save(any());
  }

  Map<String, String> callback(String code) {
    var params = new HashMap<String, String>();
    params.put("vnp_TxnRef", attempt.getId().toString());
    params.put("vnp_Amount", "1000");
    params.put("vnp_TmnCode", "test");
    params.put("vnp_ResponseCode", code);
    params.put("vnp_TransactionStatus", "00".equals(code) ? "00" : "02");
    params.put("vnp_TransactionNo", "123456");
    params.put("vnp_PayDate", "20261028130000");
    sign(params);
    return params;
  }

  void sign(Map<String, String> params) {
    params.remove("vnp_SecureHash");
    String data =
        params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                e ->
                    e.getKey()
                        + "="
                        + java.net.URLEncoder.encode(
                            e.getValue(), java.nio.charset.StandardCharsets.US_ASCII))
            .collect(java.util.stream.Collectors.joining("&"));
    params.put("vnp_SecureHash", VNPayUtil.hmacSHA512("fixture-secret", data));
  }
}
