package com.flashticket.payment_service.service.payment;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.mapper.*;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.message.StartPaymentResult;
import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import com.flashticket.payment_service.dto.response.StartRequestView;
import com.flashticket.payment_service.dto.response.StartRequestState;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.exception.PaymentQueryException;
import com.flashticket.payment_service.dto.response.PaymentView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentQueryService {
  private static final String START_CONSUMER="payment.start-requested.v1";
  private final OrderPaymentSnapshotRepository snapshots;
  private final PaymentAttemptRepository attempts;
  private final PaymentConfirmationRepository confirmations;
  private final RefundRepository refunds;
  private final InboxMessageRepository inbox;
  private final PaymentViewMapper views;
  private final PaymentStartMapper starts;
  private final RefundMapper refundMapper;
  private final MessageJsonMapper messages;

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public PaymentView getPayment(UUID orderId,UUID requestId,String buyerSubject){
    if(buyerSubject==null || buyerSubject.isBlank()) throw error(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED");
    var access=snapshots.findAccessByOrderId(orderId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"PAYMENT_NOT_READY"));
    if(!buyerSubject.equals(access.getBuyerSubject())) throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
    var request=requestId==null ? null : requestResult(orderId,requestId);
    var latest=attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(orderId)
        .map(a->starts.toDto(a,access.getExpiresAt())).orElse(null);
    return views.toDto(orderId,confirmations.findChargeIdByOrderId(orderId).orElse(null),latest,request,
        refunds.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream().map(refundMapper::toDto).toList());
  }

  private StartRequestView requestResult(UUID orderId,UUID requestId){
    var received=inbox.findById(new InboxMessageId(START_CONSUMER,requestId));
    if(received.isEmpty()) return new StartRequestView(requestId,StartRequestState.WAITING,null);
    StartPaymentResult result;
    try { result=messages.fromResultJson(received.get().getResponsePayload()); }
    catch(ContractValidationException ex) { throw unavailable(orderId,requestId); }
    if(!orderId.equals(result.orderId())) throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
    if(!requestId.equals(result.requestId())) throw unavailable(orderId,requestId);
    if(result.state()==StartPaymentResultState.CREATED){
      if(attempts.findByIdAndOrderId(result.attemptId(),orderId).isEmpty()) throw unavailable(orderId,requestId);
      return new StartRequestView(requestId,StartRequestState.CREATED,null);
    }
    return new StartRequestView(requestId,StartRequestState.REJECTED,result.failureCode());
  }

  private PaymentQueryException unavailable(UUID orderId,UUID requestId){
    log.warn("Payment request state unavailable orderId={} requestId={}",orderId,requestId);
    return error(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_STATE_UNAVAILABLE");
  }
  private static PaymentQueryException error(HttpStatus status,String code){return new PaymentQueryException(status,code);}
}
