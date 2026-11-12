package com.example.demo.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.demo.chain.adapter.out.persistence.*;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.domain.*;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.usecase.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Tag("postgres")
class PayoutConsumerTest {
  @Test
  void replayDoesNotSendAgainAndUnknownBroadcastIsDurable() {
    try (var db = new TestDatabase()) {
      var chain = mock(EthChainPort.class);
      when(chain.sender()).thenReturn("sender");
      when(chain.pendingNonce()).thenReturn(0L);
      when(chain.sign(any()))
          .thenAnswer(
              inv -> {
                ChainTransaction t = inv.getArgument(0);
                return new SignedEthTransaction("raw" + t.nonce(), "hash" + t.nonce());
              });
      var transactions = new ChainTransactionPersistenceAdapter(db.jdbc);
      var broadcasts = new BroadcastPersistenceAdapter(db.jdbc);
      var manager = new DataSourceTransactionManager(db.dataSource);
      var prepare = new PreparePayoutUseCaseImpl(chain, transactions, manager);
      var send = new SendPayoutUseCaseImpl(prepare, chain, transactions, broadcasts, manager);
      String payout = db.payout();
      String event =
          db.jdbc.queryForObject(
              "SELECT id FROM outbox_messages WHERE aggregate_id=?", String.class, payout);
      send.send(event, payout);
      send.send(event, payout);
      verify(chain, times(1)).broadcast(any());
      assertEquals("PENDING", transactions.find(payout).orElseThrow().status());
      assertTrue(broadcasts.processed(event, payout));
      String second = db.payout();
      String next =
          db.jdbc.queryForObject(
              "SELECT id FROM outbox_messages WHERE aggregate_id=?", String.class, second);
      doThrow(new IllegalStateException("response lost")).when(chain).broadcast(any());
      send.send(next, second);
      assertEquals("BROADCAST_UNKNOWN", transactions.find(second).orElseThrow().status());
      assertEquals(
          "AWAITING_CONFIRMATION",
          db.jdbc.queryForObject("SELECT status FROM payouts WHERE id=?", String.class, second));
      assertTrue(broadcasts.processed(next, second));
      assertThrows(IllegalArgumentException.class, () -> send.send("out_fake", payout));
    }
  }
}
