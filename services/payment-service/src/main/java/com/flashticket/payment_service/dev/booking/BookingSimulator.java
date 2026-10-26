package com.flashticket.payment_service.dev.booking;

import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.hashing.*;
import com.flashticket.payment_service.validation.*;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Standalone dev tool. Does not start Spring, access Payment DB or expose HTTP. */
public final class BookingSimulator {
  public static final String EXCHANGE = "payment.simulator.ex";
  public static final String ROUTING_KEY = "payment.start-requested.v1";
  public static final String QUEUE = "payment.simulator.start.q";
  public static final String DLX = "payment.simulator.dlx";
  public static final String DLQ = "payment.simulator.start.dlq";

  private BookingSimulator() {}

  public static void main(String[] args) throws Exception {
    var options = options(args);
    try (var validators = Validation.buildDefaultValidatorFactory()) {
      var validator = validators.getValidator();
      var codec = new ContractCodec(JsonMapper.builder().build(), validator);
      var purchases = new PurchaseSnapshotValidator(validator);
      var hashes = new ContractHashService(new CanonicalContractSerializer(validator), purchases);
      String json;
      Path path;
      if (options.containsKey("replay")) {
        path = Path.of(options.get("replay"));
        json = BookingCommandFile.read(path);
      } else {
        var command =
            new BookingCommandFactory(hashes, Clock.systemUTC())
                .create(
                    options.get("buyer-subject"),
                    options.getOrDefault("amount", "100000"),
                    Duration.ofSeconds(
                        Long.parseLong(options.getOrDefault("lifetime-seconds", "900"))));
        path =
            Path.of(
                options.getOrDefault(
                    "output", "target/booking-simulator/" + command.messageId() + ".json"));
        json = codec.write(command);
        BookingCommandFile.save(path, json);
      }
      var envelope = codec.read(json, new TypeReference<Envelope<StartPayment>>() {});
      new MessageInvariantValidator(purchases, hashes).validateStartPayment(envelope);
      System.out.println("Command file: " + path.toAbsolutePath());
      publish(json, envelope.messageId().toString());
      System.out.println("Broker confirmed requestId=" + envelope.messageId());
      System.out.println("orderId=" + envelope.aggregateId());
      System.out.println(
          "GET /api/v1/orders/"
              + envelope.aggregateId()
              + "/payment?requestId="
              + envelope.messageId());
    }
  }

  public static void publish(String json, String messageId) throws Exception {
    var factory = new ConnectionFactory();
    factory.setHost(env("RABBITMQ_HOST", "127.0.0.1"));
    factory.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5679")));
    factory.setUsername(env("RABBITMQ_USERNAME", "payment_simulator"));
    factory.setPassword(env("RABBITMQ_PASSWORD", "simulator-local-only"));
    factory.setVirtualHost(env("RABBITMQ_VHOST", "/"));
    factory.setConnectionTimeout(5000);
    factory.setAutomaticRecoveryEnabled(false);
    try (var connection = factory.newConnection("booking-simulator");
        var channel = connection.createChannel()) {
      channel.confirmSelect();
      var returned = new AtomicBoolean();
      channel.addReturnListener(returnMessage -> returned.set(true));
      var properties =
          new AMQP.BasicProperties.Builder()
              .contentType("application/json")
              .contentEncoding("UTF-8")
              .deliveryMode(2)
              .messageId(messageId)
              .build();
      channel.basicPublish(
          EXCHANGE, ROUTING_KEY, true, properties, json.getBytes(StandardCharsets.UTF_8));
      channel.waitForConfirmsOrDie(5000);
      if (returned.get())
        throw new IllegalStateException(
            "Unroutable command; start Payment with payment-simulator profile first");
    }
  }

  private static String env(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }

  private static Map<String, String> options(String[] args) {
    var options = new HashMap<String, String>();
    var allowed = Set.of("buyer-subject", "amount", "lifetime-seconds", "output", "replay");
    if (args.length % 2 != 0) throw new IllegalArgumentException("Expected --name value pairs");
    for (int i = 0; i < args.length; i += 2) {
      if (!args[i].startsWith("--")
          || !allowed.contains(args[i].substring(2))
          || options.putIfAbsent(args[i].substring(2), args[i + 1]) != null)
        throw new IllegalArgumentException("Unknown or repeated simulator option");
    }
    if (options.containsKey("replay") && options.size() != 1)
      throw new IllegalArgumentException("Replay accepts only --replay path");
    return options;
  }
}
