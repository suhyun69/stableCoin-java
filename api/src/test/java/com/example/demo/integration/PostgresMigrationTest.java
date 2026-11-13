package com.example.demo.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("postgres")
class PostgresMigrationTest {
  @Test
  void migrateFreshSchemaAndValidateOnSecondRun() throws Exception {
    String url =
        System.getenv()
            .getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5433/stablepay");
    String user = System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "stablepay");
    String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "stablepay");
    String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
    var flyway =
        Flyway.configure()
            .dataSource(url, user, password)
            .schemas(schema)
            .defaultSchema(schema)
            .load();
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        assertEquals(4, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        connection.setSchema(schema);
        try (var statement = connection.createStatement()) {
          try (var result =
              statement.executeQuery(
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema = '"
                      + schema
                      + "'")) {
            assertTrue(result.next());
            assertEquals(8, result.getInt(1)); // seven payout tables + Flyway history
          }
          assertThrows(
              SQLException.class,
              () ->
                  statement.executeUpdate(
                      "INSERT INTO chain_nonce_allocations VALUES ('sender', -1)"));
          statement.executeUpdate("INSERT INTO chain_nonce_allocations VALUES ('sender', 0)");
          assertThrows(
              SQLException.class,
              () ->
                  statement.executeUpdate(
                      "INSERT INTO chain_nonce_allocations VALUES ('sender', 1)"));
          try (var result =
              statement.executeQuery(
                  "SELECT count(*) FROM pg_indexes WHERE schemaname = '"
                      + schema
                      + "' AND indexname LIKE 'reconciliation_findings_open_%'")) {
            assertTrue(result.next());
            assertEquals(3, result.getInt(1));
          }
        }
      } finally {
        try (var statement = connection.createStatement()) {
          statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
      }
    }
  }

  @Test
  void upgradeBackfillsSenderFromExistingTransactionAndLegacySetting() throws Exception {
    String url =
        System.getenv()
            .getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5433/stablepay");
    String user = System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "stablepay");
    String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "stablepay");
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var old =
        Flyway.configure()
            .dataSource(url, user, password)
            .schemas(schema)
            .defaultSchema(schema)
            .target("3")
            .load();
    old.migrate();
    try (var connection = DriverManager.getConnection(url, user, password)) {
      connection.setSchema(schema);
      try (var statement = connection.createStatement()) {
        statement.execute(
            "INSERT INTO payouts (id,idempotency_key,merchant_id,recipient_address,amount_atomic,request_fingerprint,status) VALUES ('pending','key1','merchant','recipient',1,'legacy','REQUESTED'),('signed','key2','merchant','recipient',1,'legacy','REQUESTED')");
        statement.execute(
            "INSERT INTO chain_transactions (id,payout_id,sender_address,recipient_address,amount_atomic,nonce,status) VALUES ('ctx','signed','0x70997970c51812dc3a010c7d01b50e0d17dc79c8','recipient',1,0,'CREATED')");
        var upgraded =
            Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema)
                .defaultSchema(schema)
                .load();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        try (var rs =
            statement.executeQuery("SELECT sender_address FROM payouts WHERE id='signed'")) {
          assertTrue(rs.next());
          assertEquals("0x70997970c51812dc3a010c7d01b50e0d17dc79c8", rs.getString(1));
        }
        try (var rs =
            statement.executeQuery("SELECT sender_address FROM payouts WHERE id='pending'")) {
          assertTrue(rs.next());
          assertEquals(
              System.getenv()
                  .getOrDefault("SENDER_ADDRESS", "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266")
                  .toLowerCase(java.util.Locale.ROOT),
              rs.getString(1));
        }
        assertEquals(0, upgraded.migrate().migrationsExecuted);
      }
    } finally {
      try (var connection = DriverManager.getConnection(url, user, password);
          var statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }
}
