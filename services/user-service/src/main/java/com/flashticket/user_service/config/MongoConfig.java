package com.flashticket.user_service.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

/** B12 dùng validator kín, không chấp nhận field type metadata của Spring Data. */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {
  @Bean
  public static BeanPostProcessor mongoTypeMetadataCustomizer() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof MappingMongoConverter converter) {
          converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        }
        return bean;
      }
    };
  }
}
