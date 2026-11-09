package com.example.demo.chain.adapter.out.persistence;
import com.example.demo.chain.application.port.out.RecoveryStorePort;
import com.example.demo.chain.domain.ChainTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class RecoveryPersistenceAdapter implements RecoveryStorePort {
    private final JdbcTemplate jdbc;
    public RecoveryPersistenceAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<String> candidates(int limit) {
        return jdbc.queryForList("SELECT p.id FROM payouts p JOIN chain_transactions ct ON ct.payout_id=p.id WHERE p.status IN ('REQUESTED','AWAITING_CONFIRMATION') AND ct.status IN ('SIGNED','BROADCAST_UNKNOWN','PENDING') AND ct.raw_transaction IS NOT NULL AND COALESCE(ct.broadcast_at,ct.signed_at,ct.created_at)<now()-interval '10 seconds' ORDER BY COALESCE(ct.broadcast_at,ct.signed_at,ct.created_at),ct.id LIMIT ?",String.class,limit);
    }
    public void review(ChainTransaction transaction,String reason) {
        var rows=jdbc.queryForList("SELECT p.status FROM payouts p JOIN chain_transactions ct ON ct.payout_id=p.id WHERE p.id=? AND ct.transaction_hash=? FOR UPDATE OF p,ct",transaction.payoutId(),transaction.hash());
        if(rows.isEmpty()) return;
        String current=(String)rows.getFirst().get("status");
        if(!List.of("REQUESTED","AWAITING_CONFIRMATION").contains(current)) return;
        jdbc.update("UPDATE payouts SET status='REQUIRES_REVIEW',updated_at=now() WHERE id=?",transaction.payoutId());
        jdbc.update("UPDATE chain_transactions SET last_error=? WHERE payout_id=?",reason,transaction.payoutId());
        jdbc.update("INSERT INTO payout_status_history (id,payout_id,from_status,to_status,reason) VALUES (?,?,?,'REQUIRES_REVIEW',?)","hist_"+UUID.randomUUID(),transaction.payoutId(),current,reason);
    }
}
