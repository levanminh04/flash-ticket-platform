package com.flashticket.payment_service.support;

import static org.mockito.Mockito.mock;

import com.flashticket.payment_service.client.BookingPurchaseClient;
import com.flashticket.payment_service.client.VNPayRefundClient;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
public class PaymentHaInstanceConfiguration {
  @Bean
  @Primary
  BookingPurchaseClient haBookingPurchaseClient() {
    return mock(BookingPurchaseClient.class);
  }

  @Bean
  @Primary
  VNPayRefundClient haRefundClient() {
    return mock(VNPayRefundClient.class);
  }

  @Bean
  @Primary
  JwtDecoder haJwtDecoder() {
    return mock(JwtDecoder.class);
  }

  @Bean
  HibernatePropertiesCustomizer paymentSqlInspection(
      ObjectProvider<EntityManagerFactory> factories) {
    // Không resolve EMF trong lúc Hibernate đang bootstrap chính EMF đó.
    return properties ->
        properties.put(
            JdbcSettings.STATEMENT_INSPECTOR,
            (StatementInspector) sql -> PaymentPostgresFixture.inspectSql(factories, sql));
  }
}
