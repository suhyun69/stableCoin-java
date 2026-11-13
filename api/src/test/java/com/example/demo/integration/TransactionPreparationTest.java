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
      db.jdbc.update("UPDATE payouts SET sender_address='sender' WHERE id=?", payout);
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
      db.jdbc.update("UPDATE payouts SET sender_address='sender' WHERE id=?", other);
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
      db.jdbc.update("UPDATE payouts SET sender_address='sender' WHERE id=?", next);
      assertEquals(20, db.tx.execute(s -> store.prepare(next, "sender", 20)).nonce());
    }
  }

  @Test
  void concurrentRequestsKeepNonceSequencesIndependentPerSender() throws Exception {
    try (var db = new TestDatabase();
        var pool = Executors.newFixedThreadPool(8)) {
      var store = new ChainTransactionPersistenceAdapter(db.jdbc);
      List<java.util.concurrent.Future<com.example.demo.chain.domain.ChainTransaction>> results =
          new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        String id = db.payout();
        String sender = i % 2 == 0 ? "sender-a" : "sender-b";
        db.jdbc.update("UPDATE payouts SET sender_address=? WHERE id=?", sender, id);
        long initial = i % 2 == 0 ? 10 : 30;
        results.add(pool.submit(() -> db.tx.execute(s -> store.prepare(id, sender, initial))));
      }
      Map<String, Set<Long>> sequences = new HashMap<>();
      for (var result : results) {
        var tx = result.get();
        sequences.computeIfAbsent(tx.sender(), k -> new TreeSet<>()).add(tx.nonce());
      }
      assertEquals(Set.of(10L, 11L, 12L, 13L, 14L, 15L), sequences.get("sender-a"));
      assertEquals(Set.of(30L, 31L, 32L, 33L, 34L, 35L), sequences.get("sender-b"));
      assertThrows(
          com.example.demo.common.exception.ConflictException.class,
          () -> db.tx.execute(s -> store.prepare(db.payout(), "wrong-sender", 0)));
    }
  }
}
