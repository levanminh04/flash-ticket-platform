package com.flashticket.payment_service.persistence;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.config.PaymentJsonConfig;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.messaging.repository.InboxMessageRepository;
import com.flashticket.payment_service.payment.repository.OrderPaymentSnapshotRepository;
import com.flashticket.payment_service.refund.repository.EventRefundItemRepository;
import jakarta.persistence.metamodel.SingularAttribute;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DataJpaTest(
    properties = {"spring.autoconfigure.exclude=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("payment-persistence-it")
@Import({PaymentJsonConfig.class, JacksonAutoConfiguration.class})
@Sql("/fixtures/payment/persistence.sql")
class PaymentPersistenceIT {
  @Autowired TestEntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationContext context;

  @BeforeAll
  static void requireIsolatedDatabase() {
    for (String variable :
        List.of("PAYMENT_TEST_DB_URL", "PAYMENT_TEST_DB_USERNAME", "PAYMENT_TEST_DB_PASSWORD"))
      assertThat(System.getenv(variable))
          .as("Required isolated PostgreSQL test variable: %s", variable)
          .isNotBlank();
    if (System.getenv("PAYMENT_DB_URL") != null)
      assertThat(System.getenv("PAYMENT_TEST_DB_URL"))
          .as("Test database must differ from runtime")
          .isNotEqualTo(System.getenv("PAYMENT_DB_URL"));
  }

  @Test
  void validatesAllMappingsAgainstPaymentSchema() throws Exception {
    var types = em.getEntityManager().getMetamodel().getEntities();
    assertThat(types).hasSize(13);
    long versions =
        types.stream()
            .flatMap(type -> type.getSingularAttributes().stream())
            .filter(SingularAttribute::isVersion)
            .count();
    assertThat(versions).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema='payment_schema' and table_name like 'tbl_%'",
                Integer.class))
        .isEqualTo(13);
  }

  @Test
  void preservesTypedJsonAndCompositeKeys() throws Exception {
    var repository = context.getBean(OrderPaymentSnapshotRepository.class);
    var original =
        repository.findById(UUID.fromString("00000000-0000-4000-8000-000000000001")).orElseThrow();
    assertThat(original.getPurchaseSnapshot().getTotal()).isEqualTo("200");
    var mapper = context.getBean(JsonMapper.class);
    var newId = UUID.randomUUID();
    ObjectNode tree = (ObjectNode) mapper.valueToTree(original.getPurchaseSnapshot());
    tree.put("orderId", newId.toString());
    var copy = new OrderPaymentSnapshot();
    copy.setOrderId(newId);
    copy.setEventId(original.getEventId());
    copy.setBuyerSubject(original.getBuyerSubject());
    copy.setExpectedAmount(original.getExpectedAmount());
    copy.setCurrency(original.getCurrency());
    copy.setExpiresAt(original.getExpiresAt());
    copy.setSourceOrderVersion(original.getSourceOrderVersion());
    copy.setSnapshotHash("test-copy");
    copy.setReceivedAt(original.getReceivedAt());
    copy.setPurchaseSnapshot(mapper.treeToValue(tree, PurchaseSnapshot.class));
    em.persistAndFlush(copy);
    em.clear();
    assertThat(repository.findById(newId).orElseThrow().getPurchaseSnapshot().getOrderId())
        .isEqualTo(newId);
    var inboxKey = new InboxMessageId();
    inboxKey.setConsumerName("payment.start");
    inboxKey.setMessageId(UUID.fromString("00000000-0000-4000-8000-000000000007"));
    assertThat(context.getBean(InboxMessageRepository.class).findById(inboxKey)).isPresent();
    var itemKey = new EventRefundItemId();
    itemKey.setRunId(UUID.fromString("00000000-0000-4000-8000-000000000013"));
    itemKey.setOrderId(original.getOrderId());
    assertThat(context.getBean(EventRefundItemRepository.class).findById(itemKey)).isPresent();
  }

  @Test
  void enforcesLedgerForeignKeysAndUnfinishedAttemptConstraint() throws Exception {
    jdbc.update(
        "insert into payment_schema.tbl_payment_attempts(id,order_id,provider,merchant_account,expected_amount,currency,state,request_key,request_hash) values('00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000001','VNPAY','test-merchant',200,'VND','PENDING','00000000-0000-4000-8000-000000000021','test')");
    var cases =
        List.of(
            Map.entry(
                "insert into payment_schema.tbl_payment_attempts(id,order_id,provider,merchant_account,expected_amount,currency,state,request_key,request_hash) values('00000000-0000-4000-8000-000000000022','00000000-0000-4000-8000-000000000001','VNPAY','test-merchant',200,'VND','UNKNOWN','00000000-0000-4000-8000-000000000023','test')",
                "23505"),
            Map.entry("update payment_schema.tbl_payment_confirmations set amount=201", "23503"),
            Map.entry(
                "update payment_schema.tbl_charges set merchant_account='wrong-merchant'", "23503"),
            Map.entry(
                "update payment_schema.tbl_charges set order_id='00000000-0000-4000-8000-000000000099'",
                "23503"),
            Map.entry("update payment_schema.tbl_payment_attempts set currency='USD'", "23514"));
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              for (var item : cases) {
                var savepoint = connection.setSavepoint();
                assertThatThrownBy(
                        () -> {
                          try (var statement = connection.createStatement()) {
                            statement.executeUpdate(item.getKey());
                          }
                        })
                    .isInstanceOf(java.sql.SQLException.class)
                    .satisfies(
                        error ->
                            assertThat(((java.sql.SQLException) error).getSQLState())
                                .isEqualTo(item.getValue()));
                // PostgreSQL abort transaction sau lỗi; rollback savepoint trước query tiếp theo.
                connection.rollback(savepoint);
              }
              return null;
            });
  }

  @Test
  void acceptsCancellationOnlyFinanceSnapshotAndNullRefundOutcome() throws Exception {
    var marker = new EventFinanceSnapshot();
    marker.setEventId(UUID.randomUUID());
    marker.setCancellationId(UUID.randomUUID());
    marker.setCancelledAt(Instant.parse("2026-10-20T00:00:00Z"));
    marker.setReceivedAt(marker.getCancelledAt());
    em.persistAndFlush(marker);
    em.clear();
    assertThat(em.find(EventFinanceSnapshot.class, marker.getEventId()).getFeeRate()).isNull();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_refund_attempts where outcome is null and finished_at is null",
                Integer.class))
        .isEqualTo(1);
  }
}
