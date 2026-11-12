package com.example.demo.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.chain.adapter.out.persistence.ChainTransactionPersistenceAdapter;
import com.example.demo.chain.domain.SignedEthTransaction;
import com.example.demo.testsupport.TestDatabase;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;

@Tag("postgres")
class TransactionPreparationTest {
  @Test
  void concurrentPreparationAllocatesOnceAndCreationFailureRollsBackNonce() throws Exception {
    try (var db = new TestDatabase();
        var pool = Executors.newFixedThreadPool(6)) {
      var store = new ChainTransactionPersistenceAdapter(db.jdbc);
      String payout = db.payout();
      var tasks =
          java.util.stream.IntStream.range(0, 6)
              .mapToObj(
                  i -> pool.submit(() -> db.tx.execute(s -> store.prepare(payout, "sender", 10))))
              .toList();
      for (var result : tasks) assertEquals(10, result.get().nonce());
      assertEquals(
          11L,
          db.jdbc.queryForObject(
              "SELECT next_nonce FROM chain_nonce_allocations WHERE sender_address='sender'",
              Long.class));
      String other = db.payout();
      db.jdbc.execute(
          "ALTER TABLE chain_transactions ADD CONSTRAINT test_fail CHECK(false) NOT VALID");
      try {
        assertThrows(Exception.class, () -> db.tx.execute(s -> store.prepare(other, "sender", 10)));
      } finally {
        db.jdbc.execute("ALTER TABLE chain_transactions DROP CONSTRAINT test_fail");
      }
      assertEquals(
          11L,
          db.jdbc.queryForObject(
              "SELECT next_nonce FROM chain_nonce_allocations WHERE sender_address='sender'",
              Long.class));
      assertTrue(store.find(other).isEmpty());
      assertEquals(11, db.tx.execute(s -> store.prepare(other, "sender", 10)).nonce());
      db.tx.executeWithoutResult(
          s -> store.signed(payout, new SignedEthTransaction("raw1", "hash1")));
      var winner =
          db.tx.execute(s -> store.signed(payout, new SignedEthTransaction("raw2", "hash2")));
      assertEquals("raw1", winner.raw());
      assertEquals("hash1", winner.hash());
      String next = db.payout();
      assertEquals(20, db.tx.execute(s -> store.prepare(next, "sender", 20)).nonce());
    }
  }
}
