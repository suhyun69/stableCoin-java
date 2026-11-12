package com.example.demo.testsupport;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

public final class TestDatabase implements AutoCloseable {
  public final String schema = "integration_" + UUID.randomUUID().toString().replace("-", "");
  public final DriverManagerDataSource dataSource;
  public final JdbcTemplate jdbc;
  public final TransactionTemplate tx;

  public TestDatabase() {
    String url =
        System.getenv()
            .getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5433/stablepay");
    dataSource =
        new DriverManagerDataSource(
            url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
            System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "stablepay"),
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "stablepay"));
    Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  public String payout() {
    return tx.execute(
        status ->
            new com.example.demo.payout.adapter.out.persistence.PayoutPersistenceAdapter(jdbc)
                .createRequested(
                    new com.example.demo.payout.domain.PayoutRequest(
                        "integration",
                        new com.example.demo.payout.domain.EthereumAddress("0x" + "ab".repeat(20)),
                        com.example.demo.payout.domain.EthAmount.fromEth("0.001"),
                        UUID.randomUUID().toString()))
                .id());
  }

  public void close() {
    jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
  }
}
