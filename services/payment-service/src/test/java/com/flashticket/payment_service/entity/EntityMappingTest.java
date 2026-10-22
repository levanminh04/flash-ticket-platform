package com.flashticket.payment_service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.flashticket.payment_service.entity.PaymentEntityFixtures.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import com.flashticket.payment_service.entity.id.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mapping-test")
class EntityMappingTest {
  @Autowired EntityManager em;

  @Test
  void mapsAllThirteenPaymentTables() {
    assertThat(em.getMetamodel().getEntities()).hasSize(13);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void persistsAllTablesAndNavigatesCompositeRelationshipsBothWays() {
    var f=new PaymentEntityFixtures(em);em.clear();
    var s=em.find(OrderPaymentSnapshot.class,f.snapshot.getOrderId());
    assertThat(s.getAttempts()).hasSize(1);
    var a=s.getAttempts().getFirst();
    assertThat(a.getOrderSnapshot().getOrderId()).isEqualTo(s.getOrderId());
    assertThat(a.getCharges()).hasSize(2);
    var c=em.find(Charge.class,f.charge.getId());
    assertThat(c.getAttempt().getId()).isEqualTo(a.getId());
    assertThat(s.getConfirmation().getCharge().getId()).isEqualTo(c.getId());
    assertThat(c.getConfirmation().getOrderSnapshot().getOrderId()).isEqualTo(s.getOrderId());
    assertThat(c.getRefund().getCharge().getId()).isEqualTo(c.getId());
    assertThat(c.getRefund().getAttempts()).hasSize(2).allSatisfy(ra->assertThat(ra.getRefund().getId()).isEqualTo(f.refund.getId()));
    assertThat(s.getSaga().getCharge().getId()).isEqualTo(c.getId());
    assertThat(c.getSagas()).hasSize(1);
    var run=em.find(EventRefundRun.class,f.run.getId());
    assertThat(run.getItems()).hasSize(2);
    var item=em.find(EventRefundItem.class,new EventRefundItemId(run.getId(),s.getOrderId()));
    assertThat(item.getRun().getId()).isEqualTo(run.getId());
    assertThat(item.getOrderSnapshot().getOrderId()).isEqualTo(s.getOrderId());
    assertThat(item.getConfirmation().getChargeId()).isEqualTo(c.getId());
    assertThat(item.getRefund().getId()).isEqualTo(f.refund.getId());
    assertThat(s.getRefundItems()).hasSize(1);
    assertThat(c.getConfirmation().getRefundItems()).hasSize(1);
    assertThat(c.getRefund().getItems()).hasSize(1);
    assertThat(run.getItems()).filteredOn(i->i.getConfirmedChargeId()==null).singleElement().satisfies(i->{assertThat(i.getConfirmation()).isNull();assertThat(i.getRefund()).isNull();});
    var finance=em.find(EventFinanceSnapshot.class,s.getEventId());
    assertThat(finance.getOrganizerSubject()).isNull();
    assertThat(finance.getCancelledAt()).isEqualTo(NOW);
    assertThat(s.getPurchaseSnapshot().get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(s.getExpectedAmount()).isEqualByComparingTo("10");
    assertThat(em.createQuery("from InboxMessage",InboxMessage.class).getResultList()).isNotEmpty();
    run.getItems().clear();em.flush();em.clear();
    assertThat(em.find(EventRefundRun.class,run.getId()).getItems()).hasSize(2);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void databaseRejectsMismatchedCompositeAmount() {
    var f=new PaymentEntityFixtures(em);
    var invalid=make(PaymentAttempt.class,"orderId",f.snapshot.getOrderId(),"expectedAmount",new BigDecimal("11"));
    assertThatThrownBy(()->{em.persist(invalid);em.flush();}).isInstanceOf(RuntimeException.class);
  }

  @Autowired EntityManagerFactory emf;

  @Test
  void concurrentUpdatesDetectStaleVersion() {
    try(var first=emf.createEntityManager();var second=emf.createEntityManager()) {
      first.getTransaction().begin();var f=new PaymentEntityFixtures(first);first.getTransaction().commit();
      first.clear();first.getTransaction().begin();second.getTransaction().begin();
      var one=first.find(PaymentAttempt.class,f.attempt.getId());var two=second.find(PaymentAttempt.class,f.attempt.getId());
      one.setPaymentUrl("https://fixture.example/first");first.getTransaction().commit();
      two.setPaymentUrl("https://fixture.example/second");
      assertThatThrownBy(()->second.getTransaction().commit()).isInstanceOf(RollbackException.class);
    }
  }
}
