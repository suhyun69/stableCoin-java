package com.example.demo.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.DemoApplication;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.domain.SignedEthTransaction;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.usecase.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

@Tag("e2e")
class LocalPayoutE2eTest {
  // Anvil account #1 isolates this test from the RPC adapter's account #0.
  static final String KEY = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
  static final String SENDER = Credentials.create(KEY).getAddress();
  final ObjectMapper mapper = new ObjectMapper();
  final String topic = "test-e2e-" + UUID.randomUUID();
  final String group = "test-e2e-group-" + UUID.randomUUID();
  final String bootstrap =
      System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
  final String rpcUrl = System.getenv().getOrDefault("ANVIL_RPC_URL", "http://localhost:8545");

  ConfigurableApplicationContext start(TestDatabase db, boolean workers) {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("server.port", "0");
    properties.put("spring.main.banner-mode", "off");
    properties.put("logging.level.root", "WARN");
    properties.put("spring.datasource.url", db.dataSource.getUrl());
    properties.put("spring.datasource.username", db.dataSource.getUsername());
    properties.put("spring.datasource.password", db.dataSource.getPassword());
    properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
    properties.put("spring.flyway.enabled", "true");
    properties.put("spring.flyway.schemas", db.schema);
    properties.put("spring.kafka.bootstrap-servers", bootstrap);
    properties.put(
        "spring.kafka.producer.key-serializer",
        "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(
        "spring.kafka.producer.value-serializer",
        "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("spring.kafka.producer.acks", "all");
    properties.put("spring.kafka.producer.properties.enable.idempotence", "true");
    properties.put("spring.kafka.consumer.enable-auto-commit", "false");
    properties.put("spring.kafka.consumer.auto-offset-reset", "earliest");
    properties.put(
        "spring.kafka.consumer.key-deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put(
        "spring.kafka.consumer.value-deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put("spring.kafka.listener.ack-mode", "record");
    properties.put("spring.task.scheduling.pool.size", "3");
    properties.put("stablepay.messaging.enabled", "true");
    properties.put("stablepay.messaging.topic", topic);
    properties.put("stablepay.messaging.group", group);
    properties.put("stablepay.chain.enabled", "true");
    properties.put("stablepay.chain.rpc-url", rpcUrl);
    properties.put("stablepay.chain.private-keys", KEY + "," + EthRpcTest.KEY);
    properties.put("stablepay.chain.id", "31337");
    properties.put("stablepay.chain.confirmations", "2");
    properties.put("stablepay.workers.enabled", Boolean.toString(workers));
    // Poll confirmation explicitly below, then leave room to test Kafka reconciliation repair.
    properties.put("stablepay.workers.confirm-delay-ms", "60000");
    return new SpringApplicationBuilder(DemoApplication.class)
        .run(
            properties.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new));
  }

  String base(ConfigurableApplicationContext context) {
    return "http://localhost:" + ((WebServerApplicationContext) context).getWebServer().getPort();
  }

  HttpResponse<String> post(HttpClient http, String base, String path, String body, String key)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(25))
            .header("Content-Type", "application/json");
    if (key != null) builder.header("Idempotency-Key", key);
    return http.send(
        builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String request(String recipient) throws Exception {
    return mapper.writeValueAsString(
        Map.of("merchantId", "e2e", "from", SENDER, "to", recipient, "amount", "0.001"));
  }

  @FunctionalInterface
  interface Condition {
    boolean get() throws Exception;
  }

  void await(Condition condition) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.get()) return;
      Thread.sleep(100);
    }
    fail("condition did not become true within 40 seconds");
  }

  @Test
  @SuppressWarnings("unchecked")
  void resumesSignedPayoutsAcrossRestartAndReconcilesViaKafkaWithoutDoublePayment()
      throws Exception {
    List<String> topics =
        List.of(topic, topic + ".DLT", topic + ".reconciliation", topic + ".reconciliation.DLT");
    try (var db = new TestDatabase();
        var http = HttpClient.newHttpClient();
        var admin = Admin.create(Map.of("bootstrap.servers", bootstrap))) {
      String recipient1 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
      BigInteger initialBalance;
      var balanceRpc = Web3j.build(new org.web3j.protocol.http.HttpService(rpcUrl));
      try {
        initialBalance =
            balanceRpc
                .ethGetBalance(recipient1, DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();
      } finally {
        balanceRpc.shutdown();
      }
      String body1 = request(recipient1), body2 = request(recipient1);
      String id1, id2, raw1, raw2, hash1, hash2;
      long nonce1, nonce2;
      try (var first = start(db, false)) {
        var response1 = post(http, base(first), "/payouts", body1, "restart-1");
        assertEquals(202, response1.statusCode(), response1.body());
        var response2 = post(http, base(first), "/payouts", body2, "restart-2");
        assertEquals(202, response2.statusCode(), response2.body());
        id1 = mapper.readTree(response1.body()).get("id").asText();
        id2 = mapper.readTree(response2.body()).get("id").asText();
        first.getBean(PreparePayoutUseCase.class).prepare(id1);
        first.getBean(PreparePayoutUseCase.class).prepare(id2);
        raw1 =
            db.jdbc.queryForObject(
                "SELECT raw_transaction FROM chain_transactions WHERE payout_id=?",
                String.class,
                id1);
        raw2 =
            db.jdbc.queryForObject(
                "SELECT raw_transaction FROM chain_transactions WHERE payout_id=?",
                String.class,
                id2);
        hash1 =
            db.jdbc.queryForObject(
                "SELECT transaction_hash FROM chain_transactions WHERE payout_id=?",
                String.class,
                id1);
        hash2 =
            db.jdbc.queryForObject(
                "SELECT transaction_hash FROM chain_transactions WHERE payout_id=?",
                String.class,
                id2);
        nonce1 =
            db.jdbc.queryForObject(
                "SELECT nonce FROM chain_transactions WHERE payout_id=?", Long.class, id1);
        nonce2 =
            db.jdbc.queryForObject(
                "SELECT nonce FROM chain_transactions WHERE payout_id=?", Long.class, id2);
        assertEquals(nonce1 + 1, nonce2);
        // Broadcast #1 but omit the DB result, then restart. #2 has only been signed.
        first.getBean(EthChainPort.class).broadcast(new SignedEthTransaction(raw1, hash1));
        assertEquals(2, first.getBean(PublishOutboxUseCase.class).publish());
        String payload =
            db.jdbc.queryForObject(
                "SELECT payload::text FROM outbox_messages WHERE aggregate_id=?",
                String.class,
                id1);
        ((KafkaTemplate<String, String>) first.getBean(KafkaTemplate.class))
            .send(topic, "31337:" + SENDER, payload)
            .get();
      }
      try (var second = start(db, true)) {
        var confirm = second.getBean(ConfirmPayoutsUseCase.class);
        await(
            () -> {
              confirm.confirm();
              return db.jdbc.queryForObject(
                      "SELECT count(*) FROM payouts WHERE status='SETTLED'", Integer.class)
                  == 2;
            });
        await(
            () ->
                db.jdbc.queryForObject("SELECT count(*) FROM processed_messages", Integer.class)
                    == 2);
        await(
            () ->
                admin
                        .listConsumerGroupOffsets(group)
                        .partitionsToOffsetAndMetadata()
                        .get()
                        .entrySet()
                        .stream()
                        .filter(e -> e.getKey().topic().equals(topic))
                        .mapToLong(e -> e.getValue().offset())
                        .sum()
                    >= 3);
        assertEquals(
            raw1,
            db.jdbc.queryForObject(
                "SELECT raw_transaction FROM chain_transactions WHERE payout_id=?",
                String.class,
                id1));
        assertEquals(
            raw2,
            db.jdbc.queryForObject(
                "SELECT raw_transaction FROM chain_transactions WHERE payout_id=?",
                String.class,
                id2));
        assertEquals(
            hash2,
            db.jdbc.queryForObject(
                "SELECT transaction_hash FROM chain_transactions WHERE payout_id=?",
                String.class,
                id2));
        assertEquals(
            nonce2 + 1,
            db.jdbc.queryForObject("SELECT next_nonce FROM chain_nonce_allocations", Long.class));
        var replay = post(http, base(second), "/payouts", body1, "restart-1");
        assertEquals(202, replay.statusCode());
        assertEquals(id1, mapper.readTree(replay.body()).get("id").asText());
        var status =
            http.send(
                HttpRequest.newBuilder(URI.create(base(second) + "/payouts/" + id1)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(
            "CONFIRMED", mapper.readTree(status.body()).path("chain").path("status").asText());
        var chain = second.getBean(EthChainPort.class);
        assertEquals(hash1, chain.transaction(hash1).orElseThrow().hash());
        var rpc = second.getBean(Web3j.class);
        assertEquals(
            initialBalance.add(new BigInteger("2000000000000000")),
            rpc.ethGetBalance(recipient1, DefaultBlockParameterName.LATEST).send().getBalance());
        long from =
            db.jdbc.queryForObject(
                "SELECT min(included_block_number) FROM chain_transactions", Long.class);
        long to =
            db.jdbc.queryForObject(
                "SELECT max(included_block_number) FROM chain_transactions", Long.class);
        db.tx.executeWithoutResult(
            s -> {
              db.jdbc.update("UPDATE payouts SET status='AWAITING_CONFIRMATION' WHERE id=?", id1);
              db.jdbc.update(
                  "UPDATE chain_transactions SET status='PENDING' WHERE payout_id=?", id1);
            });
        var reconciliation =
            post(
                http,
                base(second),
                "/internal/reconciliation/runs",
                mapper.writeValueAsString(
                    Map.of("fromBlock", Long.toString(from), "toBlock", Long.toString(to))),
                null);
        assertEquals(202, reconciliation.statusCode(), reconciliation.body());
        await(
            () ->
                db.jdbc.queryForObject(
                        "SELECT count(*) FROM reconciliation_findings WHERE kind='STATUS_LAG' AND status='RESOLVED'",
                        Integer.class)
                    == 1);
        assertEquals(
            "SETTLED",
            db.jdbc.queryForObject("SELECT status FROM payouts WHERE id=?", String.class, id1));
        assertEquals(
            0,
            db.jdbc.queryForObject(
                "SELECT count(*) FROM reconciliation_findings WHERE status='OPEN'", Integer.class));
        assertEquals(
            400,
            post(
                    http,
                    base(second),
                    "/internal/reconciliation/runs",
                    "{\"fromBlock\":\"0\",\"toBlock\":\"1000\"}",
                    null)
                .statusCode());
        var health =
            http.send(
                HttpRequest.newBuilder(URI.create(base(second) + "/dependencies/chain"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(31337, mapper.readTree(health.body()).get("chainId").asInt());
      } finally {
        admin.deleteTopics(topics).all().get();
      }
    }
  }
}
