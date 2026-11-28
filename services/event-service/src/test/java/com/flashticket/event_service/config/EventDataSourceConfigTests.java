package com.flashticket.event_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EventDataSourceConfigTests {
  HikariDataSource writeDs;
  HikariDataSource readDs;
  Connection writeConnection;
  Connection readConnection;
  DataSource dataSource;

  @BeforeEach
  void setUp() throws SQLException {
    writeDs = mock(HikariDataSource.class);
    readDs = mock(HikariDataSource.class);
    writeConnection = mock(Connection.class);
    readConnection = mock(Connection.class);
    when(writeDs.getConnection()).thenReturn(writeConnection);
    when(readDs.getConnection()).thenReturn(readConnection);
    when(writeConnection.getAutoCommit()).thenReturn(true);
    when(writeConnection.getTransactionIsolation())
        .thenReturn(Connection.TRANSACTION_READ_COMMITTED);
    when(writeConnection.createStatement()).thenReturn(mock(Statement.class));
    when(readConnection.createStatement()).thenReturn(mock(Statement.class));
  }

  @AfterEach
  void clearTransactionContext() {
    TransactionSynchronizationManager.clear();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void bindsBothPoolsAndKeepsConnectionContracts() throws Exception {
    var methods = Arrays.stream(EventDataSourceConfig.class.getDeclaredMethods()).toList();
    assertThat(methods).extracting(java.lang.reflect.Method::getName).contains("writeDs", "readDs");
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "fixture",
                Map.of(
                    "PG_RW_HOST",
                    "writer.fixture",
                    "PG_RO_HOST",
                    "reader.fixture",
                    "PG_DB",
                    "event_db",
                    "PG_USER",
                    "fixture-user",
                    "PG_PASSWORD",
                    "fixture-password")));
    var yaml =
        new org.springframework.boot.env.YamlPropertySourceLoader()
            .load(
                "bundled",
                new org.springframework.core.io.ClassPathResource(
                    "service-config/application.yaml"));
    yaml.forEach(environment.getPropertySources()::addLast);
    assertThat(environment.containsProperty("spring.datasource.url")).isFalse();
    assertThat(environment.containsProperty("event.read-replica.enabled")).isFalse();
    for (String name : new String[] {"writeDs", "readDs"}) {
      var factory = EventDataSourceConfig.class.getDeclaredMethod(name);
      var prefix = factory.getAnnotation(ConfigurationProperties.class).value();
      assertThat(prefix).isEqualTo(name.equals("writeDs") ? "app.db.write" : "app.db.read");
      try (var pool = (HikariDataSource) factory.invoke(new EventDataSourceConfig())) {
        Binder.get(environment).bind(prefix, Bindable.ofInstance(pool));
        assertThat(pool.getJdbcUrl())
            .isEqualTo(
                name.equals("writeDs")
                    ? "jdbc:postgresql://writer.fixture:5432/event_db"
                    : "jdbc:postgresql://reader.fixture:5432/event_db");
        assertThat(pool.getMaximumPoolSize()).isEqualTo(name.equals("writeDs") ? 5 : 2);
        assertThat(pool.getSchema()).isEqualTo("event_schema");
        assertThat(pool.getUsername()).isEqualTo("fixture-user");
        assertThat(pool.getPassword()).isEqualTo("fixture-password");
        assertThat(pool.getMinimumIdle()).isEqualTo(name.equals("writeDs") ? 1 : 0);
        assertThat(pool.getDataSourceProperties().getProperty("sslmode")).isEqualTo("require");
        assertThat(pool.getDataSourceProperties().getProperty("targetServerType"))
            .isEqualTo(name.equals("writeDs") ? "primary" : "secondary");
        if (name.equals("writeDs")) {
          assertThat(pool.getConnectionInitSql()).isEqualTo("SET ROLE event_owner");
          assertThat(pool.getDataSourceProperties().getProperty("reWriteBatchedInserts"))
              .isEqualTo("true");
        } else {
          assertThat(pool.getConnectionInitSql()).isNull();
          assertThat(pool.isReadOnly()).isTrue();
          assertThat(pool.getConnectionTimeout()).isEqualTo(5000);
          assertThat(pool.getDataSourceProperties().getProperty("connectTimeout")).isEqualTo("5");
          assertThat(pool.getDataSourceProperties().getProperty("socketTimeout")).isEqualTo("5");
        }
        assertThat(pool.isRunning()).isFalse();
        environment
            .getPropertySources()
            .addFirst(
                new MapPropertySource(
                    "pool-override", Map.of("PG_WRITE_POOL_SIZE", "3", "PG_READ_POOL_SIZE", "1")));
        Binder.get(environment).bind(prefix, Bindable.ofInstance(pool));
        assertThat(pool.getMaximumPoolSize()).isEqualTo(name.equals("writeDs") ? 3 : 1);
        environment.getPropertySources().remove("pool-override");
      }
    }
  }

  @Test
  void readOnlyFlagSelectsReadDatasource() throws Exception {
    dataSource =
        (DataSource)
            EventDataSourceConfig.class
                .getDeclaredMethod("dataSource", HikariDataSource.class, HikariDataSource.class)
                .invoke(new EventDataSourceConfig(), writeDs, readDs);
    clearInvocations(writeDs, readDs);
    TransactionSynchronizationManager.setCurrentTransactionName("not-a-whitelist");
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute("SELECT 1");
    }
    verify(readDs).getConnection();
    verify(writeDs, never()).getConnection();
  }

  @Test
  void ordinaryTransactionSelectsWriteDatasource() throws Exception {
    dataSource =
        (DataSource)
            EventDataSourceConfig.class
                .getDeclaredMethod("dataSource", HikariDataSource.class, HikariDataSource.class)
                .invoke(new EventDataSourceConfig(), writeDs, readDs);
    clearInvocations(writeDs, readDs);
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute("SELECT 1");
    }
    verify(writeDs).getConnection();
    verify(readDs, never()).getConnection();
  }

  @Test
  void sqlOutsideTransactionSelectsWriteDatasource() throws Exception {
    dataSource =
        (DataSource)
            EventDataSourceConfig.class
                .getDeclaredMethod("dataSource", HikariDataSource.class, HikariDataSource.class)
                .invoke(new EventDataSourceConfig(), writeDs, readDs);
    clearInvocations(writeDs, readDs);
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute("SELECT 1");
    }
    verify(writeDs).getConnection();
    verify(readDs, never()).getConnection();
  }

  @Test
  void lazyProxyDefersRequestConnectionUntilStatement() throws Exception {
    dataSource =
        (DataSource)
            EventDataSourceConfig.class
                .getDeclaredMethod("dataSource", HikariDataSource.class, HikariDataSource.class)
                .invoke(new EventDataSourceConfig(), writeDs, readDs);
    assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
    clearInvocations(writeDs, readDs);
    try (var connection = dataSource.getConnection()) {
      connection.setReadOnly(true);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      verifyNoInteractions(writeDs, readDs);
      TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
      try (var statement = connection.createStatement()) {
        statement.execute("SELECT 1");
      }
      verify(readDs).getConnection();
      verify(writeDs, never()).getConnection();
    }
  }

  @Test
  void dbHealthQueriesOnlyWriteDatasource() throws Exception {
    var metadata = mock(java.sql.DatabaseMetaData.class);
    when(writeConnection.getMetaData()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(writeConnection.isValid(anyInt())).thenReturn(true);
    var indicator = new EventDataSourceConfig().dbHealthContributor(writeDs);
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    verify(writeDs, atLeastOnce()).getConnection();
    verifyNoInteractions(readDs);
    for (String name : new String[] {"writeDs", "readDs"}) {
      assertThat(
              EventDataSourceConfig.class
                  .getDeclaredMethod(name)
                  .getAnnotation(Bean.class)
                  .defaultCandidate())
          .isFalse();
    }
    assertThat(
            EventDataSourceConfig.class
                .getDeclaredMethod("dataSource", HikariDataSource.class, HikariDataSource.class)
                .isAnnotationPresent(Primary.class))
        .isTrue();
  }
}
