package com.example.demo.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class PostgresMigrationTest {
    @Test
    void migrateFreshSchemaAndValidateOnSecondRun() throws Exception {
        String url = System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5433/stablepay");
        String user = System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "stablepay");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "stablepay");
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema)
                .defaultSchema(schema).load();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try {
                assertEquals(2, flyway.migrate().migrationsExecuted);
                assertEquals(0, flyway.migrate().migrationsExecuted);
                flyway.validate();
                connection.setSchema(schema);
                try (var statement = connection.createStatement()) {
                    try (var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = '" + schema + "'")) {
                        assertTrue(result.next());
                        assertEquals(9, result.getInt(1)); // seven payout tables + Todo + Flyway history
                    }
                    assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO chain_nonce_allocations VALUES ('sender', -1)"));
                    statement.executeUpdate("INSERT INTO chain_nonce_allocations VALUES ('sender', 0)");
                    assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO chain_nonce_allocations VALUES ('sender', 1)"));
                    try (var result = statement.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname = '" + schema + "' AND indexname LIKE 'reconciliation_findings_open_%'")) {
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
}
