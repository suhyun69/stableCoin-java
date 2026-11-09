package com.example.demo.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.demo.usecase.CreatePayoutUseCase;
import com.example.demo.usecase.dto.CreatePayoutAppRequest;
import com.example.demo.common.exception.ConflictException;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PayoutPostgresTest {
    static final String SCHEMA = "payout_test_" + UUID.randomUUID().toString().replace("-", "");
    static final String URL = System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5433/stablepay");
    static final String USER = System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "stablepay");
    static final String PASSWORD = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "stablepay");
    static final String ADDRESS = "0x" + "ab".repeat(20);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired CreatePayoutUseCase create;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @AfterAll static void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
    CreatePayoutAppRequest request(String key, String amount) { return new CreatePayoutAppRequest("merchant", ADDRESS, amount, key); }
    @Test void concurrentRetriesCreateOneBundle() throws Exception {
        String key = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = java.util.stream.IntStream.range(0, 6).mapToObj(i -> executor.submit(() -> create.create(request(key, "0.1")))).toList();
            String id = futures.getFirst().get().id();
            for (var future : futures) assertEquals(id, future.get().id());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payout_status_history WHERE payout_id=?", Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE aggregate_id=?", Integer.class, id));
            assertEquals(id, create.create(request(key, "0.10")).id());
            assertThrows(ConflictException.class, () -> create.create(request(key, "0.2")));
        }
    }
    @Test void outboxFailureRollsBackPayoutAndHistory() {
        String key = UUID.randomUUID().toString();
        jdbc.execute("ALTER TABLE outbox_messages ADD CONSTRAINT test_fail CHECK (false) NOT VALID");
        try {
            assertThrows(Exception.class, () -> create.create(request(key, "0.1")));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payouts WHERE idempotency_key=?", Integer.class, key));
        } finally { jdbc.execute("ALTER TABLE outbox_messages DROP CONSTRAINT test_fail"); }
    }
    @Test void readsPayoutBeforeAndAfterTransactionCreation() throws Exception {
        var payout = create.create(request(UUID.randomUUID().toString(), "0.1"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/payouts/" + payout.id()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED")).andExpect(jsonPath("$.chain").isEmpty());
        jdbc.update("INSERT INTO chain_transactions (id,payout_id,sender_address,recipient_address,amount_atomic,nonce,status) VALUES (?,?,?,?,?,0,'CREATED')",
            "ctx_" + UUID.randomUUID(), payout.id(), ADDRESS, ADDRESS, new java.math.BigDecimal("100000000000000000"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/payouts/" + payout.id()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.chain.status").value("CREATED")).andExpect(jsonPath("$.chain.nonce").value("0"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/payouts/missing")).andExpect(status().isNotFound());
    }
    @Test void httpContract() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"merchantId\":\"merchant\",\"recipientAddress\":\"" + ADDRESS + "\",\"amount\":\"0.1\"}";
        mvc.perform(post("/payouts").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/payouts").header("Idempotency-Key", key).contentType("application/json").content(body))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("REQUESTED"));
        mvc.perform(post("/payouts").header("Idempotency-Key", key).contentType("application/json").content(body.replace("0.1", "0.2")))
            .andExpect(status().isConflict());
    }
}
