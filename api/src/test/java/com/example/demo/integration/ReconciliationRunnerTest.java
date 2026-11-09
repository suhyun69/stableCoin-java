package com.example.demo.integration;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.chain.adapter.out.persistence.*;
import com.example.demo.reconciliation.adapter.out.persistence.ReconciliationPersistenceAdapter;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.domain.*;
import com.example.demo.usecase.impl.RunReconciliationUseCaseImpl;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("postgres")
class ReconciliationRunnerTest {
    @Test void concurrentRunsRepairLagAndDeduplicateEthFindingsWithoutLogIndex() throws Exception {
        try(var db=new TestDatabase();var pool=Executors.newFixedThreadPool(2)) {
            var transactions=new ChainTransactionPersistenceAdapter(db.jdbc);var chain=mock(EthChainPort.class);
            when(chain.sender()).thenReturn("sender");when(chain.head()).thenReturn(11L);when(chain.blockHash(10)).thenReturn("block");
            var run=new RunReconciliationUseCaseImpl(chain,new ReconciliationPersistenceAdapter(db.jdbc),transactions,new ConfirmationPersistenceAdapter(db.jdbc),new RecoveryPersistenceAdapter(db.jdbc),new DataSourceTransactionManager(db.dataSource),2);
            String id=db.payout();var saved=db.tx.execute(s -> transactions.prepare(id,"sender",0));
            db.tx.execute(s -> transactions.signed(id,new SignedEthTransaction("raw","hash")));
            var observed=new EthTransfer("hash","sender",saved.recipient(),saved.amount(),0,10L,"block");
            var unknown=new EthTransfer("unknown","sender",saved.recipient(),saved.amount(),1,10L,"block");
            when(chain.transfers(10,10)).thenReturn(List.of(observed,unknown));
            when(chain.receipt("hash")).thenReturn(Optional.of(new EthReceipt("hash",true,10,"block")));
            when(chain.receipt("unknown")).thenReturn(Optional.of(new EthReceipt("unknown",true,10,"block")));
            var first=pool.submit(() -> run.run("10","10"));var second=pool.submit(() -> run.run("10","10"));first.get();second.get();
            assertEquals("SETTLED",db.jdbc.queryForObject("SELECT status FROM payouts WHERE id=?",String.class,id));
            assertEquals(1,db.jdbc.queryForObject("SELECT count(*) FROM reconciliation_findings WHERE status='OPEN' AND transaction_hash='unknown' AND log_index IS NULL",Integer.class));
            assertEquals(1,db.jdbc.queryForObject("SELECT count(*) FROM reconciliation_findings WHERE status='RESOLVED' AND kind='STATUS_LAG'",Integer.class));
            String repaired=db.payout();db.tx.execute(s -> transactions.prepare(repaired,"sender",0));db.tx.execute(s -> transactions.signed(repaired,new SignedEthTransaction("raw2","unknown")));
            run.run("10","10");
            assertEquals(0,db.jdbc.queryForObject("SELECT count(*) FROM reconciliation_findings WHERE status='OPEN'",Integer.class));
            when(chain.transfers(10,10)).thenReturn(List.of());run.run("10","10");run.run("10","10");
            assertEquals("REQUIRES_REVIEW",db.jdbc.queryForObject("SELECT status FROM payouts WHERE id=?",String.class,id));
            assertEquals(2,db.jdbc.queryForObject("SELECT count(*) FROM reconciliation_findings WHERE status='OPEN' AND kind='MISSING_CHAIN_TRANSACTION'",Integer.class));
            assertThrows(IllegalStateException.class,() -> run.run("12","12"));
        }
    }
}
