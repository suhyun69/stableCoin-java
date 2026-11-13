package com.example.demo.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.chain.adapter.out.persistence.*;
import com.example.demo.chain.adapter.out.rpc.*;
import com.example.demo.chain.domain.*;
import com.example.demo.payout.adapter.out.persistence.PayoutPersistenceAdapter;
import com.example.demo.payout.application.PayoutService;
import com.example.demo.reconciliation.adapter.out.persistence.ReconciliationPersistenceAdapter;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.usecase.dto.CreatePayoutAppRequest;
import com.example.demo.usecase.impl.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Tag("chain")
class MultiSenderTest {
  static final String KEY1 = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";

  @Test
  void signsBothSendersAndRecoversSavedRawWithoutRegisteredSigningKey() throws Exception {
    var rpc =
        Web3j.build(
            new HttpService(
                System.getenv().getOrDefault("ANVIL_RPC_URL", "http://localhost:8545")));
    try (var db = new TestDatabase()) {
      var wallets = new AnvilWalletRegistry(EthRpcTest.KEY + "," + KEY1);
      var chain = new Web3jEthAdapter(rpc, wallets, 31337);
      chain.validateAnvilAccounts();
      var foreignWallet = new AnvilWalletRegistry("0".repeat(63) + "1");
      assertThrows(
          IllegalArgumentException.class,
          () -> new Web3jEthAdapter(rpc, foreignWallet, 31337).validateAnvilAccounts());
      var addresses = new ArrayList<>(wallets.addresses());
      var create =
          new CreatePayoutUseCaseImpl(
              new PayoutService(new PayoutPersistenceAdapter(db.jdbc)), wallets);
      var transactions = new ChainTransactionPersistenceAdapter(db.jdbc);
      var broadcasts = new BroadcastPersistenceAdapter(db.jdbc);
      var manager = new DataSourceTransactionManager(db.dataSource);
      var prepare = new PreparePayoutUseCaseImpl(chain, transactions, manager);
      Map<String, SignedEthTransaction> signed = new LinkedHashMap<>();
      Map<String, Long> nonces = new HashMap<>();
      for (int i = 0; i < 2; i++) {
        String from = addresses.get(i), to = addresses.get(1 - i);
        long initial = chain.pendingNonce(from);
        String id =
            db.tx
                .execute(
                    s ->
                        create.create(
                            new CreatePayoutAppRequest(
                                "multi", from, to, "0.001", UUID.randomUUID().toString())))
                .id();
        prepare.prepare(id);
        var saved = transactions.find(id).orElseThrow();
        assertEquals(initial, saved.nonce());
        nonces.put(from, initial);
        var decoded = (SignedRawTransaction) TransactionDecoder.decode(saved.raw());
        assertEquals(from, decoded.getFrom());
        assertEquals(to, decoded.getTo());
        signed.put(id, new SignedEthTransaction(saved.raw(), saved.hash()));
      }
      assertEquals(
          2, db.jdbc.queryForObject("SELECT count(*) FROM chain_nonce_allocations", Integer.class));
      // First transaction reached Anvil, but the application lost its broadcast result.
      chain.broadcast(signed.values().iterator().next());
      db.jdbc.update("UPDATE chain_transactions SET signed_at=now()-interval '11 seconds'");
      // Recovery must work even after one of the signing keys is removed.
      var recoveryChain = new Web3jEthAdapter(rpc, EthRpcTest.KEY, 31337);
      var recovery =
          new RecoverBroadcastsUseCaseImpl(
              recoveryChain,
              transactions,
              broadcasts,
              new RecoveryPersistenceAdapter(db.jdbc),
              manager);
      assertEquals(2, recovery.recover());
      var confirmationStore = new ConfirmationPersistenceAdapter(db.jdbc);
      var confirm =
          new ConfirmPayoutsUseCaseImpl(chain, transactions, confirmationStore, manager, 1);
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (System.nanoTime() < deadline) {
        confirm.confirm();
        if (db.jdbc.queryForObject(
                "SELECT count(*) FROM payouts WHERE status='SETTLED'", Integer.class)
            == 2) break;
        Thread.sleep(100);
      }
      assertEquals(
          2,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM payouts WHERE status='SETTLED'", Integer.class));
      for (var entry : signed.entrySet()) {
        var saved = transactions.find(entry.getKey()).orElseThrow();
        assertEquals(entry.getValue().raw(), saved.raw());
        assertEquals(entry.getValue().hash(), saved.hash());
        var observed = chain.transaction(saved.hash()).orElseThrow();
        assertEquals(saved.sender(), observed.from());
        assertEquals(saved.recipient(), observed.to());
        assertEquals(
            nonces.get(saved.sender()) + 1,
            db.jdbc.queryForObject(
                "SELECT next_nonce FROM chain_nonce_allocations WHERE sender_address=?",
                Long.class,
                saved.sender()));
      }
      long fromBlock =
          db.jdbc.queryForObject(
              "SELECT min(included_block_number) FROM chain_transactions", Long.class);
      long toBlock =
          db.jdbc.queryForObject(
              "SELECT max(included_block_number) FROM chain_transactions", Long.class);
      db.jdbc.update("UPDATE payouts SET status='AWAITING_CONFIRMATION'");
      db.jdbc.update("UPDATE chain_transactions SET status='PENDING'");
      var reconcile =
          new RunReconciliationUseCaseImpl(
              recoveryChain,
              new ReconciliationPersistenceAdapter(db.jdbc),
              transactions,
              confirmationStore,
              new RecoveryPersistenceAdapter(db.jdbc),
              manager,
              1);
      reconcile.run(Long.toString(fromBlock), Long.toString(toBlock));
      assertEquals(
          2,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM payouts WHERE status='SETTLED'", Integer.class));
      assertEquals(
          2,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM reconciliation_findings WHERE kind='STATUS_LAG' AND status='RESOLVED'",
              Integer.class));
    } finally {
      rpc.shutdown();
    }
  }
}
