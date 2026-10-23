package com.flashticket.payment_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.flashticket.payment_service.entity.PaymentReadFixtures.*;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.mapper.*;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import com.flashticket.payment_service.dto.response.StartRequestState;
import com.flashticket.payment_service.exception.PaymentQueryException;
import com.flashticket.payment_service.service.payment.PaymentQueryService;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import java.util.*;
import org.junit.jupiter.api.*;

class PaymentQueryServiceTest {
  static final PaymentContractFixtures F=new PaymentContractFixtures();
  final OrderPaymentSnapshotRepository snapshots=mock(OrderPaymentSnapshotRepository.class);
  final PaymentAttemptRepository attempts=mock(PaymentAttemptRepository.class);
  final PaymentConfirmationRepository confirmations=mock(PaymentConfirmationRepository.class);
  final RefundRepository refunds=mock(RefundRepository.class);
  final InboxMessageRepository inbox=mock(InboxMessageRepository.class);
  final PaymentQueryService service=new PaymentQueryService(snapshots,attempts,confirmations,refunds,inbox,
      new PaymentViewMapperImpl(),new PaymentStartMapperImpl(new MoneyMapper()),new RefundMapperImpl(new MoneyMapper()),new MessageJsonMapper(F.codec));
  final UUID order=UUID.randomUUID();
  @AfterAll static void close(){F.close();}
  @BeforeEach void setup(){
    var access=mock(OrderPaymentSnapshotRepository.OrderPaymentAccess.class);
    when(access.getOrderId()).thenReturn(order);when(access.getBuyerSubject()).thenReturn("buyer");when(access.getExpiresAt()).thenReturn(TIME.plusSeconds(600));
    when(snapshots.findAccessByOrderId(order)).thenReturn(Optional.of(access));
  }
  @Test void returnsCurrentStateWithoutInventingRequestResult(){
    var a=attempt(null,order,UUID.randomUUID(),PaymentAttemptState.UNKNOWN);
    when(attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(order)).thenReturn(Optional.of(a));
    var charge=UUID.randomUUID(); when(confirmations.findChargeIdByOrderId(order)).thenReturn(Optional.of(charge));
    var view=service.getPayment(order,null,"buyer");
    assertThat(view.orderId()).isEqualTo(order);assertThat(view.acceptedChargeId()).isEqualTo(charge);
    assertThat(view.latestAttempt().amount()).isEqualTo("10");assertThat(view.latestAttempt().paymentUrl()).isNull();assertThat(view.startRequest()).isNull();assertThat(view.refunds()).isEmpty();
    var request=UUID.randomUUID();assertThat(service.getPayment(order,request,"buyer").startRequest().state()).isEqualTo(StartRequestState.WAITING);
  }
  @Test void checksOwnerBeforeReadingSensitiveData(){
    failure(()->service.getPayment(UUID.randomUUID(),null,"buyer"),404,"PAYMENT_NOT_READY");
    failure(()->service.getPayment(order,null,"other"),404,"RESOURCE_NOT_FOUND");
    failure(()->service.getPayment(order,null," "),401,"AUTHENTICATION_REQUIRED");
    verifyNoInteractions(attempts,confirmations,refunds,inbox);
  }
  @Test void readsExactCommandResultRatherThanLatestAttempt(){
    var request=UUID.randomUUID();var old=attempt(null,order,UUID.randomUUID(),PaymentAttemptState.FAILED);
    when(inbox.findById(any())).thenReturn(Optional.of(inbox(request,order,StartPaymentResultState.CREATED,old.getId())));
    when(attempts.findByIdAndOrderId(old.getId(),order)).thenReturn(Optional.of(old));
    assertThat(service.getPayment(order,request,"buyer").startRequest().state()).isEqualTo(StartRequestState.CREATED);
    when(inbox.findById(any())).thenReturn(Optional.of(inbox(request,order,StartPaymentResultState.REJECTED,null)));
    assertThat(service.getPayment(order,request,"buyer").startRequest().failureCode()).isEqualTo("ORDER_EXPIRED");
  }
  @Test void refusesForeignOrCorruptInboxResults(){
    var request=UUID.randomUUID();
    when(inbox.findById(any())).thenReturn(Optional.of(inbox(request,UUID.randomUUID(),StartPaymentResultState.REJECTED,null)));
    failure(()->service.getPayment(order,request,"buyer"),404,"RESOURCE_NOT_FOUND");
    when(inbox.findById(any())).thenReturn(Optional.of(inbox(UUID.randomUUID(),order,StartPaymentResultState.REJECTED,null)));
    failure(()->service.getPayment(order,request,"buyer"),503,"PAYMENT_STATE_UNAVAILABLE");
    when(inbox.findById(any())).thenReturn(Optional.of(inbox(request,order,StartPaymentResultState.CREATED,UUID.randomUUID())));
    failure(()->service.getPayment(order,request,"buyer"),503,"PAYMENT_STATE_UNAVAILABLE");
    var broken=inbox(request,order,StartPaymentResultState.REJECTED,null);broken.setResponsePayload(F.mapper.readTree("{}"));
    when(inbox.findById(any())).thenReturn(Optional.of(broken));
    failure(()->service.getPayment(order,request,"buyer"),503,"PAYMENT_STATE_UNAVAILABLE");
  }
  static void failure(Runnable action,int status,String code){
    var e=catchThrowable(action::run);assertThat(e).isInstanceOf(PaymentQueryException.class);
    assertThat(((PaymentQueryException)e).status().value()).isEqualTo(status);assertThat(((PaymentQueryException)e).code()).isEqualTo(code);
  }
}
