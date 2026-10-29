package com.flashticket.booking_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.flashticket.booking_service.client.internal")
@EnableResilientMethods
public class BookingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BookingServiceApplication.class, args);
  }
}
