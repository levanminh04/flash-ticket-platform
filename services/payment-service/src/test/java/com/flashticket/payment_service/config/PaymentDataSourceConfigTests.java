package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentDataSourceConfigTests {
  HikariDataSource writer;
  HikariDataSource reader;
  Connection writeConnection;
  Connection readConnection;
  PreparedStatement writeStatement;
  PreparedStatement readStatement;
  DataSource dataSource;

  @BeforeEach
  void setUp() throws SQLException {
    TransactionSynchronizationManager.clear();
    writer = mock(HikariDataSource.class);
    reader = mock(HikariDataSource.class);
    writeConnection = mock(Connection.class);
    readConnection = mock(Connection.class);
    writeStatement = mock(PreparedStatement.class);
    readStatement = mock(PreparedStatement.class);
    when(writer.getConnection()).thenReturn(writeConnection);
    when(reader.getConnection()).thenReturn(readConnection);
    for (var connection : new Connection[] {writeConnection, readConnection}) {
      when(connection.getAutoCommit()).thenReturn(true);
      when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
    }
    when(writeConnection.prepareStatement("select 1")).thenReturn(writeStatement);
    when(readConnection.prepareStatement("select 1")).thenReturn(readStatement);
    dataSource = new PaymentDataSourceConfig().dataSource(writer, reader);
    assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
    // Default-property lookup lúc factory bootstrap không phải query của request.
    ((LazyConnectionDataSourceProxy) dataSource).checkDefaultConnectionProperties();
    clearInvocations(writer, reader, writeConnection, readConnection);
  }

  @AfterEach
  void clearTransactionContext() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void bindsBothHikariPrefixesAndKeepsWriterDefaults() throws Exception {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "fixtureInputs",
                Map.of(
                    "PG_RW_HOST",
                    "writer.fixture",
                    "PG_RO_HOST",
                    "replica.fixture",
                    "PG_DB",
                    "payment_db",
                    "PG_USER",
                    "fixture",
                    "PG_PASSWORD",
                    "fixture-only")));
    for (var source :
        new YamlPropertySourceLoader()
            .load("payment", new FileSystemResource("src/main/resources/application.yaml"))) {
      environment.getPropertySources().addLast(source);
    }
    try (var write = new HikariDataSource();
        var read = new HikariDataSource()) {
      var binder = Binder.get(environment);
      assertThat(binder.bind("app.db.write", Bindable.ofInstance(write)).isBound()).isTrue();
      assertThat(binder.bind("app.db.read", Bindable.ofInstance(read)).isBound()).isTrue();
      assertThat(write.getJdbcUrl()).isEqualTo("jdbc:postgresql://writer.fixture:5432/payment_db");
      assertThat(read.getJdbcUrl()).isEqualTo("jdbc:postgresql://replica.fixture:5432/payment_db");
      assertThat(write.getUsername()).isEqualTo("fixture");
      assertThat(read.getPassword()).isEqualTo("fixture-only");
      assertThat(write.getMaximumPoolSize()).isEqualTo(10);
      assertThat(read.getMaximumPoolSize()).isEqualTo(10);
      assertThat(write.getSchema()).isEqualTo("payment_schema");
      assertThat(read.getSchema()).isEqualTo("payment_schema");
      assertThat(write.getConnectionInitSql()).isEqualTo("SET ROLE payment_owner");
      assertThat(read.isReadOnly()).isTrue();
      assertThat(write.getDataSourceProperties())
          .containsEntry("sslmode", "require")
          .containsEntry("targetServerType", "primary");
      assertThat(read.getDataSourceProperties())
          .containsEntry("sslmode", "require")
          .containsEntry("targetServerType", "secondary");
      environment
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "poolBudget", Map.of("PG_WRITE_POOL_SIZE", "5", "PG_READ_POOL_SIZE", "2")));
      binder.bind("app.db.write", Bindable.ofInstance(write));
      binder.bind("app.db.read", Bindable.ofInstance(read));
      assertThat(write.getMaximumPoolSize()).isEqualTo(5);
      assertThat(read.getMaximumPoolSize()).isEqualTo(2);
      assertThat(environment.containsProperty("spring.datasource.url")).isFalse();
      assertThat(environment.containsProperty("payment.report-replica.enabled")).isFalse();
    }
  }

  @Test
  void routesReadOnlyFlagToRead() throws Exception {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    TransactionSynchronizationManager.setCurrentTransactionName("ordinary-read-only");
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.prepareStatement("select 1")).isSameAs(readStatement);
    }
    verifyNoInteractions(writer);
  }

  @Test
  void routesWriteFlagToWrite() throws Exception {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.prepareStatement("select 1")).isSameAs(writeStatement);
    }
    verifyNoInteractions(reader);
  }

  @Test
  void routesWithoutTransactionToWrite() throws Exception {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.prepareStatement("select 1")).isSameAs(writeStatement);
    }
    verifyNoInteractions(reader);
  }

  @Test
  void defersRequestConnectionUntilStatement() throws Exception {
    try (var connection = dataSource.getConnection()) {
      verifyNoInteractions(writer, reader);
      TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
      assertThat(connection.prepareStatement("select 1")).isSameAs(readStatement);
    }
    verifyNoInteractions(writer);
  }

  @Test
  void dbHealthUsesOnlyWriter() throws Exception {
    var metadata = mock(DatabaseMetaData.class);
    when(writeConnection.getMetaData()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(writeConnection.isValid(anyInt())).thenReturn(true);
    var statement = mock(Statement.class);
    var rows = mock(ResultSet.class);
    var rowMetadata = mock(java.sql.ResultSetMetaData.class);
    when(writeConnection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
    when(rows.getMetaData()).thenReturn(rowMetadata);
    when(rowMetadata.getColumnCount()).thenReturn(1);
    when(rows.getObject(1)).thenReturn(1);
    var health = new PaymentDataSourceConfig().dbHealthContributor(writer).health();
    assertThat(health.getStatus().getCode()).isEqualTo("UP");
    verifyNoInteractions(reader);
    var rawPools =
        Arrays.stream(PaymentDataSourceConfig.class.getDeclaredMethods())
            .filter(method -> method.getReturnType() == HikariDataSource.class)
            .toList();
    assertThat(rawPools).hasSize(2);
    assertThat(rawPools)
        .allSatisfy(
            method -> {
              assertThat(method.getAnnotation(Bean.class).defaultCandidate()).isFalse();
              assertThat(method.getAnnotation(Bean.class).destroyMethod()).isEqualTo("close");
            });
  }
}
