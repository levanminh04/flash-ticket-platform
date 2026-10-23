package com.flashticket.payment_service.repository;

import static org.assertj.core.api.Assertions.*;
import static com.flashticket.payment_service.entity.PaymentReadFixtures.*;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"mapping-test","local-foundation"})
class PaymentReadRepositoriesTest {
  @Autowired EntityManager em;
  @Autowired OrderPaymentSnapshotRepository snapshots;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired PaymentConfirmationRepository confirmations;
  @Autowired RefundRepository refunds;
  @Autowired InboxMessageRepository inbox;

  @Test @Transactional void readsOnlyRequestedOrderAndSortsAttemptTies() {
    var s = snapshot(em,"buyer"); var other = snapshot(em,"other");
    var small = UUID.fromString("10000000-0000-0000-0000-000000000010");
    var large = UUID.fromString("10000000-0000-0000-0000-000000000020");
    attempt(em,s.getOrderId(),large,PaymentAttemptState.FAILED);
    attempt(em,s.getOrderId(),small,PaymentAttemptState.FAILED);
    attempt(em,other.getOrderId(),UUID.randomUUID(),PaymentAttemptState.PENDING);
    em.flush(); em.clear();
    assertThat(snapshots.findAccessByOrderId(s.getOrderId())).isPresent();
    var access = snapshots.findAccessByOrderId(s.getOrderId()).orElseThrow();
    assertThat(access.getBuyerSubject()).isEqualTo("buyer");
    assertThat(access.getExpiresAt()).isEqualTo(TIME.plusSeconds(600));
    assertThat(attempts.findFirstByOrderIdOrderByStartedAtDescIdDesc(s.getOrderId()).orElseThrow().getId()).isEqualTo(large);
    assertThat(attempts.findByIdAndOrderId(large,other.getOrderId())).isEmpty();
    assertThat(snapshots.findAccessByOrderId(UUID.randomUUID())).isEmpty();
  }

  @Test @Transactional void readsConfirmationRefundsAndCompositeInboxIdentity() {
    var s=snapshot(em,"buyer"); var a=attempt(em,s.getOrderId(),UUID.randomUUID(),PaymentAttemptState.SUCCEEDED);
    var c=charge(em,a); confirm(em,c); var c2=charge(em,a);
    var small=UUID.fromString("20000000-0000-0000-0000-000000000010");
    var large=UUID.fromString("20000000-0000-0000-0000-000000000020");
    refund(em,c,large); refund(em,c2,small);
    var request=UUID.randomUUID(); em.persist(inbox(request,s.getOrderId(),StartPaymentResultState.CREATED,a.getId()));
    em.flush(); em.clear();
    assertThat(confirmations.findChargeIdByOrderId(s.getOrderId())).contains(c.getId());
    assertThat(confirmations.findChargeIdByOrderId(UUID.randomUUID())).isEmpty();
    assertThat(refunds.findAllByOrderIdOrderByCreatedAtAscIdAsc(s.getOrderId())).extracting(r->r.getId()).containsExactly(small,large);
    assertThat(refunds.findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID.randomUUID())).isEmpty();
    assertThat(inbox.findById(new InboxMessageId("payment.start-requested.v1",request))).isPresent();
    assertThat(inbox.findById(new InboxMessageId("other",request))).isEmpty();
  }
}
