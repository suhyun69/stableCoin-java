package com.example.demo.integration;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.chain.adapter.out.persistence.*;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.domain.*;
import com.example.demo.usecase.impl.RecoverBroadcastsUseCaseImpl;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("postgres")
class BroadcastRecoveryTest {
    @Test void reusesSavedRawAfterCrashAndReviewsConsumedNonce() {
        try(var db=new TestDatabase()) {
            var transactions=new ChainTransactionPersistenceAdapter(db.jdbc);var broadcasts=new BroadcastPersistenceAdapter(db.jdbc);
            var chain=mock(EthChainPort.class);when(chain.sender()).thenReturn("sender");
            var recover=new RecoverBroadcastsUseCaseImpl(chain,transactions,broadcasts,new RecoveryPersistenceAdapter(db.jdbc),new DataSourceTransactionManager(db.dataSource));
            String id=db.payout();db.tx.execute(s -> transactions.prepare(id,"sender",0));db.tx.execute(s -> transactions.signed(id,new SignedEthTransaction("saved-raw","saved-hash")));
            db.jdbc.update("UPDATE chain_transactions SET signed_at=now()-interval '11 seconds'");
            doThrow(new IllegalStateException("response lost")).when(chain).broadcast(any());
            recover.recover();assertEquals("BROADCAST_UNKNOWN",transactions.find(id).orElseThrow().status());
            verify(chain).broadcast(new SignedEthTransaction("saved-raw","saved-hash"));
            db.jdbc.update("UPDATE chain_transactions SET broadcast_at=now()-interval '11 seconds'");
            when(chain.transaction("saved-hash")).thenReturn(Optional.of(new EthTransfer("saved-hash","sender","recipient",java.math.BigInteger.ONE,0,null,null)));
            recover.recover();assertEquals("PENDING",transactions.find(id).orElseThrow().status());
            verify(chain,times(1)).broadcast(any());
            assertEquals(1L,db.jdbc.queryForObject("SELECT next_nonce FROM chain_nonce_allocations",Long.class));
            String conflict=db.payout();db.tx.execute(s -> transactions.prepare(conflict,"sender",0));db.tx.execute(s -> transactions.signed(conflict,new SignedEthTransaction("conflict-raw","conflict-hash")));
            db.jdbc.update("UPDATE chain_transactions SET signed_at=now()-interval '11 seconds' WHERE payout_id=?",conflict);
            when(chain.confirmedNonce()).thenReturn(5L);
            recover.recover();recover.recover();
            assertEquals("REQUIRES_REVIEW",db.jdbc.queryForObject("SELECT status FROM payouts WHERE id=?",String.class,conflict));
            assertEquals(2,db.jdbc.queryForObject("SELECT count(*) FROM payout_status_history WHERE payout_id=?",Integer.class,conflict));
            verify(chain,times(1)).broadcast(any());
        }
    }
}
