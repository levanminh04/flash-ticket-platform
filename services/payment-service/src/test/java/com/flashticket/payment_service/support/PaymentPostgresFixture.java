package com.flashticket.payment_service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.payment_service.PaymentServiceApplication;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Chỉ sở hữu container disposable; không nạp endpoint hoặc secret của ứng dụng thật. */
public final class PaymentPostgresFixture {
  private static Network network;
  private static GenericContainer<?> primary;
  private static GenericContainer<?> standby;
  private static GenericContainer<?> rabbit;
  private static String databasePassword;
  private static String brokerPassword;
  public static final ThreadLocal<List<Map<String, Object>>> OBSERVATIONS = new ThreadLocal<>();
  public static final ThreadLocal<UnaryOperator<String>> SQL_TRANSFORM = new ThreadLocal<>();

  private PaymentPostgresFixture() {}

  public static synchronized void startInfrastructure() throws Exception {
    if (primary != null) return;
    databasePassword = UUID.randomUUID().toString();
    brokerPassword = UUID.randomUUID().toString();
    String replicationPassword = UUID.randomUUID().toString();
    network = Network.newNetwork();
    Runtime.getRuntime().addShutdownHook(new Thread(PaymentPostgresFixture::stopInfrastructure));
    try {
      primary =
          new GenericContainer<>("postgres:16.15")
              .withNetwork(network)
              .withNetworkAliases("payment-ha-primary")
              .withEnv("POSTGRES_DB", "payment_db")
              .withEnv("POSTGRES_PASSWORD", databasePassword)
              .withEnv("PAYMENT_TEST_REPLICATION_PASSWORD", replicationPassword)
              .withCommand("postgres", "-c", "wal_level=replica", "-c", "max_wal_senders=8")
              .withExposedPorts(5432)
              .withCreateContainerCmdModifier(
                  command ->
                      command
                          .getHostConfig()
                          .withPortBindings(
                              new PortBinding(
                                  Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432))))
              .waitingFor(
                  Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
      primary.start();
      var setup =
          primary.execInContainer(
              "bash",
              "-euc",
              """
          psql -X -w -q -v ON_ERROR_STOP=1 -U postgres -d payment_db <<'SQL'
          \\getenv replication_password PAYMENT_TEST_REPLICATION_PASSWORD
          CREATE ROLE payment_ha_replicator WITH LOGIN REPLICATION PASSWORD :'replication_password';
          SQL
          printf '\\nhost replication payment_ha_replicator samenet scram-sha-256\\n' >> "$PGDATA/pg_hba.conf"
          psql -X -w -q -v ON_ERROR_STOP=1 -U postgres -d payment_db -c 'SELECT pg_reload_conf()'
          """);
      assertThat(setup.getExitCode()).as("Thiết lập replication fixture").isZero();
      standby =
          new GenericContainer<>("postgres:16.15")
              .withNetwork(network)
              .withEnv("PGDATA", "/var/lib/postgresql/data/standby")
              .withEnv("PAYMENT_TEST_REPLICATION_PASSWORD", replicationPassword)
              .withCommand(
                  "bash",
                  "-euc",
                  """
              install -d -m 700 -o postgres -g postgres "$PGDATA"
              passfile=/var/lib/postgresql/data/standby.pgpass
              umask 077
              printf 'payment-ha-primary:5432:*:payment_ha_replicator:%s\\n' "$PAYMENT_TEST_REPLICATION_PASSWORD" > "$passfile"
              chown postgres:postgres "$passfile"
              unset PAYMENT_TEST_REPLICATION_PASSWORD
              gosu postgres pg_basebackup --dbname="host=payment-ha-primary port=5432 user=payment_ha_replicator passfile=$passfile connect_timeout=5" --pgdata="$PGDATA" -X stream -R --no-password
              exec docker-entrypoint.sh postgres -c hot_standby=on -c max_standby_streaming_delay=1s -c hot_standby_feedback=off
              """)
              .withExposedPorts(5432)
              .withCreateContainerCmdModifier(
                  command ->
                      command
                          .getHostConfig()
                          .withPortBindings(
                              new PortBinding(
                                  Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432))))
              .waitingFor(
                  Wait.forLogMessage(
                      ".*database system is ready to accept read-only connections.*\\n", 1));
      standby.start();
      assertThat(psql(false, "select pg_is_in_recovery()")).isEqualTo("f");
      assertThat(psql(true, "select pg_is_in_recovery()")).isEqualTo("t");
      initializeSchema();
      rabbit =
          new GenericContainer<>("rabbitmq:4.3.6-management")
              .withNetwork(network)
              .withEnv("RABBITMQ_DEFAULT_USER", "payment_test")
              .withEnv("RABBITMQ_DEFAULT_PASS", brokerPassword)
              .withExposedPorts(5672)
              .withCreateContainerCmdModifier(
                  command ->
                      command
                          .getHostConfig()
                          .withPortBindings(
                              new PortBinding(
                                  Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5672))))
              .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));
      rabbit.start();
    } catch (Exception | AssertionError failure) {
      stopInfrastructure();
      throw failure;
    }
  }

  public static synchronized void stopInfrastructure() {
    if (rabbit != null) {
      rabbit.stop();
      rabbit = null;
    }
    if (standby != null) {
      standby.stop();
      standby = null;
    }
    if (primary != null) {
      primary.stop();
      primary = null;
    }
    if (network != null) {
      network.close();
      network = null;
    }
  }

  public static Map<String, Object> connectionProperties(boolean readerEnabled) throws Exception {
    startInfrastructure();
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(
        "spring.datasource.url",
        "jdbc:postgresql://127.0.0.1:"
            + primary.getMappedPort(5432)
            + "/payment_db?connectTimeout=5&socketTimeout=5");
    properties.put("spring.datasource.username", "postgres");
    properties.put("spring.datasource.password", databasePassword);
    properties.put("spring.datasource.hikari.schema", "payment_schema");
    properties.put("spring.datasource.hikari.connection-init-sql", "SET ROLE payment_owner");
    properties.put("spring.datasource.hikari.maximum-pool-size", 5);
    properties.put("spring.datasource.hikari.minimum-idle", 1);
    properties.put("spring.rabbitmq.host", "127.0.0.1");
    properties.put("spring.rabbitmq.port", rabbit.getMappedPort(5672));
    properties.put("spring.rabbitmq.username", "payment_test");
    properties.put("spring.rabbitmq.password", brokerPassword);
    properties.put("spring.rabbitmq.dynamic", true);
    // Giữ lựa chọn worker của từng profile; mapping-test không có RabbitTemplate.
    properties.put("payment.worker.initial-delay", 86400000);
    properties.put("spring.rabbitmq.listener.simple.auto-startup", false);
    properties.put("payment.report-replica.enabled", readerEnabled);
    if (readerEnabled) {
      properties.put(
          "payment.report-replica.datasource.jdbc-url",
          "jdbc:postgresql://127.0.0.1:"
              + standby.getMappedPort(5432)
              + "/payment_db?targetServerType=secondary&connectTimeout=5&socketTimeout=5");
      properties.put("payment.report-replica.datasource.username", "payment_ha_reader");
      properties.put("payment.report-replica.datasource.password", databasePassword);
      properties.put("payment.report-replica.datasource.schema", "payment_schema");
      properties.put("payment.report-replica.datasource.read-only", true);
      properties.put("payment.report-replica.datasource.maximum-pool-size", 2);
      properties.put("payment.report-replica.datasource.minimum-idle", 0);
      properties.put("payment.report-replica.datasource.connection-timeout", 5000);
      // Không scheduler tự đổi mẫu giữa một phép thử fault/expiry; probe được gọi tường minh.
      properties.put("payment.report-replica.probe-interval", "PT24H");
      properties.put("payment.report-replica.probe-query-timeout", 2);
      properties.put("payment.report-replica.max-sample-age", "PT5S");
      properties.put("payment.report-replica.reader-transaction-timeout", 10);
    }
    return properties;
  }

  public static void registerProperties(DynamicPropertyRegistry registry, boolean readerEnabled)
      throws Exception {
    connectionProperties(readerEnabled).forEach((key, value) -> registry.add(key, () -> value));
  }

  public static String psql(boolean replica, String sql) throws Exception {
    var result =
        (replica ? standby : primary)
            .execInContainer(
                "psql",
                "-X",
                "-w",
                "-qAt",
                "-v",
                "ON_ERROR_STOP=1",
                "-U",
                "postgres",
                "-d",
                "payment_db",
                "-c",
                sql);
    assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    return result.getStdout().strip();
  }

  private static void initializeSchema() throws Exception {
    psql(
        false,
        "create role payment_owner nologin; create schema payment_schema authorization payment_owner;");
    Path repository =
        Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize().toAbsolutePath();
    primary.copyFileToContainer(
        MountableFile.forHostPath(
            repository.resolve("docs/architecture/data/postgres/30-payment.sql")),
        "/tmp/30-payment.sql");
    primary.copyFileToContainer(
        MountableFile.forHostPath(repository.resolve("services/99-table-prefix.sql")),
        "/tmp/99-table-prefix.sql");
    var result =
        primary.execInContainer(
            "psql",
            "-X",
            "-w",
            "-v",
            "ON_ERROR_STOP=1",
            "-U",
            "postgres",
            "-d",
            "payment_db",
            "-f",
            "/tmp/30-payment.sql",
            "-f",
            "/tmp/99-table-prefix.sql");
    assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    // Login đọc riêng trong fixture để permission fault có ý nghĩa, không dùng superuser reader.
    psql(
        false,
        "create role payment_ha_reader login password '"
            + databasePassword
            + "'; grant pg_monitor to payment_ha_reader; grant usage on schema payment_schema to payment_ha_reader; grant select on all tables in schema payment_schema to payment_ha_reader");
    awaitReplay();
    assertThat(psql(true, "select to_regclass('payment_schema.tbl_refunds') is not null"))
        .isEqualTo("t");
  }

  public static void awaitReplay() throws Exception {
    String flushed = psql(false, "select pg_current_wal_flush_lsn()");
    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        psql(true, "select pg_last_wal_replay_lsn() >= '" + flushed + "'::pg_lsn"))
                    .isEqualTo("t"));
  }

  public static void resetDatabase() throws Exception {
    psql(true, "select pg_wal_replay_resume()");
    psql(
        false,
        "TRUNCATE payment_schema.tbl_order_payment_snapshots, payment_schema.tbl_event_finance_snapshots, payment_schema.tbl_event_refund_runs, payment_schema.tbl_event_payouts, payment_schema.tbl_outbox_messages, payment_schema.tbl_inbox_messages CASCADE");
    // Chỉ purge queues của broker fixture; không để redelivery từ ca trước lọt sang ca sau.
    var factory = new com.rabbitmq.client.ConnectionFactory();
    factory.setHost("127.0.0.1");
    factory.setPort(rabbit.getMappedPort(5672));
    factory.setUsername("payment_test");
    factory.setPassword(brokerPassword);
    try (var connection = factory.newConnection();
        var channel = connection.createChannel()) {
      for (String suffix :
          new String[] {
            "finance-configured",
            "event-cancelled",
            "decision.quorum",
            "issuance.quorum",
            "completed.quorum"
          }) {
        String queue = "payment.gov156.test." + suffix;
        channel.queueDeclare(queue, true, false, false, Map.of("x-queue-type", "quorum"));
        channel.queuePurge(queue);
      }
    }
    OBSERVATIONS.remove();
    SQL_TRANSFORM.remove();
    awaitReplay();
  }

  public static com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot purchase(
      org.springframework.context.ApplicationContext context, UUID eventId, String buyerSubject)
      throws Exception {
    var codec = context.getBean(com.flashticket.payment_service.dto.common.ContractCodec.class);
    var json = context.getBean(tools.jackson.databind.json.JsonMapper.class);
    var node =
        (tools.jackson.databind.node.ObjectNode)
            json.readTree(
                new org.springframework.core.io.ClassPathResource("contracts/purchase.json")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    node.put("orderId", UUID.randomUUID().toString())
        .put("eventId", eventId.toString())
        .put("buyerSubject", buyerSubject)
        .put("sourceOrderVersion", "1")
        .put("subtotal", "100000")
        .put("discount", "0")
        .put("total", "100000")
        .put("frozenAt", java.time.Instant.now().minusSeconds(1).toString())
        .put("expiresAt", java.time.Instant.now().plusSeconds(600).toString());
    ((tools.jackson.databind.node.ObjectNode) node.withArray("items").get(0))
        .put("unitPrice", "100000")
        .put("lineSubtotal", "100000");
    return codec.fromTree(
        node, com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot.class);
  }

  public static UUID seedConfirmedOrder(
      org.springframework.context.ApplicationContext context, UUID eventId, String organizerSubject)
      throws Exception {
    var codec = context.getBean(com.flashticket.payment_service.dto.common.ContractCodec.class);
    var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    var purchase = purchase(context, eventId, "buyer-1");
    var orderId = purchase.orderId();
    var attemptId = UUID.randomUUID();
    var chargeId = UUID.randomUUID();
    var now = java.time.Instant.now();
    // Ledger cố định100000/fee7.5%: kỳ vọng độc lập là7500/92500.
    jdbc.update(
        "insert into payment_schema.tbl_event_finance_snapshots(event_id,organizer_subject,event_ends_at,fee_rate,source_fee_version,received_at) values (?,?,?,0.075,1,?) on conflict (event_id) do nothing",
        eventId,
        organizerSubject,
        java.sql.Timestamp.from(now.minusSeconds(3600)),
        java.sql.Timestamp.from(now));
    jdbc.update(
        "insert into payment_schema.tbl_order_payment_snapshots(order_id,event_id,buyer_subject,expected_amount,currency,expires_at,source_order_version,purchase_snapshot,snapshot_hash,received_at) values (?,?,'buyer-1',100000,'VND',?,1,?::jsonb,?,?)",
        orderId,
        eventId,
        java.sql.Timestamp.from(purchase.expiresAt()),
        codec.write(purchase),
        orderId.toString(),
        java.sql.Timestamp.from(now));
    jdbc.update(
        "insert into payment_schema.tbl_payment_attempts(id,order_id,provider,merchant_account,expected_amount,currency,started_at,finished_at,state,request_key,request_hash,payment_url) values (?,?,'VNPAY','test',100000,'VND',?,?,'SUCCEEDED',?,?,'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_CreateDate=20261102123045')",
        attemptId,
        orderId,
        java.sql.Timestamp.from(now.minusSeconds(1)),
        java.sql.Timestamp.from(now),
        UUID.randomUUID(),
        attemptId.toString());
    jdbc.update(
        "insert into payment_schema.tbl_charges(id,attempt_id,order_id,provider,merchant_account,provider_charge_id,amount,currency,charged_at,recorded_at) values (?,?,?,'VNPAY','test',?,100000,'VND',?,?)",
        chargeId,
        attemptId,
        orderId,
        chargeId.toString(),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));
    jdbc.update(
        "insert into payment_schema.tbl_payment_confirmations(order_id,charge_id,amount,currency,confirmed_at) values (?,?,100000,'VND',?)",
        orderId,
        chargeId,
        java.sql.Timestamp.from(now));
    jdbc.update(
        "insert into payment_schema.tbl_payment_sagas(order_id,charge_id,state,command_id,next_attempt_at,created_at,updated_at) values (?,?,'COMPLETED',?,?,?,?)",
        orderId,
        chargeId,
        UUID.randomUUID(),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));
    return orderId;
  }

  public static Map<String, String> signedIpn(
      com.flashticket.payment_service.dto.response.PaymentStart attempt) {
    var ipn = new java.util.TreeMap<String, String>();
    ipn.put("vnp_TmnCode", "test");
    ipn.put("vnp_Amount", "10000000");
    ipn.put("vnp_ResponseCode", "00");
    ipn.put("vnp_TransactionStatus", "00");
    ipn.put("vnp_TransactionNo", UUID.randomUUID().toString());
    ipn.put("vnp_PayDate", "20261030010000");
    ipn.put("vnp_TxnRef", attempt.attemptId().toString());
    var signature = new StringBuilder();
    for (var entry : ipn.entrySet()) {
      if (!signature.isEmpty()) signature.append('&');
      signature.append(entry.getKey()).append('=').append(entry.getValue());
    }
    ipn.put(
        "vnp_SecureHash",
        com.flashticket.payment_service.util.VNPayUtil.hmacSHA512(
            "fixture-secret", signature.toString()));
    return ipn;
  }

  public static List<Map<String, Object>> inspectRead(Callable<?> request) throws Exception {
    var observations = new ArrayList<Map<String, Object>>();
    OBSERVATIONS.set(observations);
    try {
      request.call();
      return List.copyOf(observations);
    } finally {
      OBSERVATIONS.remove();
      SQL_TRANSFORM.remove();
    }
  }

  public static String inspectSql(ObjectProvider<EntityManagerFactory> factories, String sql) {
    var observations = OBSERVATIONS.get();
    if (observations == null) return sql;
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    var holder =
        (EntityManagerHolder) TransactionSynchronizationManager.getResource(factories.getObject());
    assertThat(holder).as("EntityManager transaction-bound").isNotNull();
    var session = holder.getEntityManager().unwrap(SessionImplementor.class);
    var connection = session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
    try (var statement = connection.createStatement();
        var row =
            statement.executeQuery(
                "select pg_is_in_recovery(), pg_backend_pid(), current_setting('transaction_isolation'), current_setting('transaction_read_only'), pg_current_snapshot()::text")) {
      assertThat(row.next()).isTrue();
      observations.add(
          Map.of(
              "replica",
              row.getBoolean(1),
              "pid",
              row.getInt(2),
              "isolation",
              row.getString(3),
              "readOnly",
              row.getString(4),
              "snapshot",
              row.getString(5),
              "sql",
              sql));
    } catch (SQLException failure) {
      throw new AssertionError("Không quan sát được connection JPA", failure);
    }
    var transform = SQL_TRANSFORM.get();
    return transform == null ? sql : transform.apply(sql);
  }

  public static ConfigurableApplicationContext openServiceContext(boolean readerEnabled)
      throws Exception {
    Map<String, Object> properties = connectionProperties(readerEnabled);
    properties.put("server.address", "127.0.0.1");
    properties.put("server.port", 0);
    return new SpringApplicationBuilder(
            PaymentServiceApplication.class, PaymentHaInstanceConfiguration.class)
        .web(WebApplicationType.SERVLET)
        .profiles("payment-flow-test")
        .initializers(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("paymentHaFixture", properties)))
        .run();
  }
}
