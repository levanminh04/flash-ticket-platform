package com.flashticket.payment_service.config;

import static com.flashticket.payment_service.dev.booking.BookingSimulator.*;

import com.flashticket.payment_service.service.start.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.*;

/** Dedicated local topology. It never declares/rebinds the business exchange or queue. */
@Configuration(proxyBeanMethods = false)
@Profile("payment-simulator & !prod & !production")
@EnableRabbit
public class PaymentSimulatorConfiguration {
  @Bean
  RabbitAdmin simulatorRabbitAdmin(ConnectionFactory connections) {
    return new RabbitAdmin(connections);
  }

  @Bean
  Declarables simulatorTopology() {
    var exchange = new DirectExchange(EXCHANGE, true, false);
    var dead = new DirectExchange(DLX, true, false);
    var queue =
        QueueBuilder.durable(QUEUE)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(ROUTING_KEY)
            .build();
    var dlq = QueueBuilder.durable(DLQ).build();
    return new Declarables(
        exchange,
        dead,
        queue,
        dlq,
        BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
        BindingBuilder.bind(dlq).to(dead).with(ROUTING_KEY));
  }

  @Bean
  BookingPaymentGuard simulatorBookingGuard(Clock clock) {
    return purchase ->
        purchase.expiresAt().isAfter(clock.instant().plusSeconds(1)) ? null : "ORDER_EXPIRED";
  }

  @Bean
  SimpleRabbitListenerContainerFactory simulatorListenerFactory(ConnectionFactory connections) {
    var factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connections);
    // AUTO acknowledges only after handle() returns, after the store's transaction commits.
    factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
    factory.setPrefetchCount(1);
    factory.setDefaultRequeueRejected(false);
    factory.setAdviceChain(
        org.springframework.amqp.rabbit.config.RetryInterceptorBuilder.stateless()
            .maxRetries(2)
            .backOffOptions(200, 2, 1000)
            .recoverer(
                (message, cause) -> {
                  throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                      "Simulator command rejected");
                })
            .build());
    factory.setErrorHandler(
        error -> {
          throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
              "Simulator command failed; inspect DLQ");
        });
    return factory;
  }

  @Bean
  SimulatorListener simulatorListener(StartPaymentProcessor processor) {
    return new SimulatorListener(processor);
  }

  public static final class SimulatorListener {
    private final StartPaymentProcessor processor;

    SimulatorListener(StartPaymentProcessor processor) {
      this.processor = processor;
    }

    @RabbitListener(queues = QUEUE, containerFactory = "simulatorListenerFactory")
    public void receive(Message message) {
      processor.handle(new String(message.getBody(), StandardCharsets.UTF_8));
    }
  }
}
