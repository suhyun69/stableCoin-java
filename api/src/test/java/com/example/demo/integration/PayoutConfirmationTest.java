package com.example.demo.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.demo.chain.adapter.out.persistence.*;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.domain.*;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.usecase.impl.ConfirmPayoutsUseCaseImpl;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Tag("postgres")
class PayoutConfirmationTest {
  @Test
  void settlesIdempotentlyAndSavesFailureAndReview() {
    try (var db = new TestDatabase()) {
      var transactions = new ChainTransactionPersistenceAdapter(db.jdbc);
      var store = new ConfirmationPersistenceAdapter(db.jdbc);
      var chain = mock(EthChainPort.class);
      when(chain.head()).thenReturn(11L);
      when(chain.blockHash(10)).thenReturn("block");
      var confirm =
          new ConfirmPayoutsUseCaseImpl(
              chain, transactions, store, new DataSourceTransactionManager(db.dataSource), 2);
      for (String kind : List.of("success", "reverted", "mismatch")) {
        String id = db.payout();
        var saved = db.tx.execute(s -> transactions.prepare(id, "sender", 0));
        db.tx.execute(s -> transactions.signed(id, new SignedEthTransaction("raw", kind)));
        when(chain.receipt(kind))
            .thenReturn(Optional.of(new EthReceipt(kind, !kind.equals("reverted"), 10, "block")));
        when(chain.transaction(kind))
            .thenReturn(
                Optional.of(
                    new EthTransfer(
                        kind,
                        "sender",
                        saved.recipient(),
                        kind.equals("mismatch") ? java.math.BigInteger.ONE : saved.amount(),
                        saved.nonce(),
                        10L,
                        "block")));
        confirm.confirm();
        confirm.confirm();
        assertEquals(
            switch (kind) {
              case "success" -> "SETTLED";
              case "reverted" -> "FAILED";
              default -> "REQUIRES_REVIEW";
            },
            db.jdbc.queryForObject("SELECT status FROM payouts WHERE id=?", String.class, id));
        if (kind.equals("success")) {
          assertEquals("CONFIRMED", transactions.find(id).orElseThrow().status());
          assertEquals(
              3,
              db.jdbc.queryForObject(
                  "SELECT count(*) FROM payout_status_history WHERE payout_id=?",
                  Integer.class,
                  id));
        }
      }
    }
  }
}
