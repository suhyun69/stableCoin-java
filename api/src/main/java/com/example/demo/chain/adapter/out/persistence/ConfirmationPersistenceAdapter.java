package com.example.demo.chain.adapter.out.persistence;
import com.example.demo.chain.application.port.out.ConfirmationStorePort;
import com.example.demo.chain.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ConfirmationPersistenceAdapter implements ConfirmationStorePort {
    private final JdbcTemplate jdbc;
    public ConfirmationPersistenceAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<String> candidates(int limit) {
        return jdbc.queryForList("SELECT p.id FROM payouts p JOIN chain_transactions ct ON ct.payout_id=p.id WHERE p.status IN ('REQUESTED','AWAITING_CONFIRMATION') AND ct.status IN ('SIGNED','BROADCAST_UNKNOWN','PENDING','MINED_SUCCESS') AND ct.transaction_hash IS NOT NULL ORDER BY p.updated_at,p.id LIMIT ?",String.class,limit);
    }
    public void touch(String payoutId) {jdbc.update("UPDATE payouts SET updated_at=now() WHERE id=?",payoutId);}
    private void transition(String id,String from,String to,String reason) {
        if(from.equals(to)) return;
        jdbc.update("UPDATE payouts SET status=?,updated_at=now() WHERE id=?",to,id);
        jdbc.update("INSERT INTO payout_status_history (id,payout_id,from_status,to_status,reason) VALUES (?,?,?,?,?)","hist_"+UUID.randomUUID(),id,from,to,reason);
    }
    public void apply(ChainTransaction expected,EthReceipt receipt,ConfirmationDecision decision) {
        var rows=jdbc.queryForList("SELECT p.status payout_status,ct.status chain_status,ct.transaction_hash FROM payouts p JOIN chain_transactions ct ON ct.payout_id=p.id WHERE p.id=? FOR UPDATE OF p,ct",expected.payoutId());
        if(rows.isEmpty()) return;
        var row=rows.getFirst();String current=(String)row.get("payout_status");
        if(!Objects.equals(row.get("transaction_hash"),expected.hash()) || "REQUIRES_REVIEW".equals(current)) return;
        if("REQUIRES_REVIEW".equals(decision.kind())) {
            transition(expected.payoutId(),current,"REQUIRES_REVIEW",decision.reason());return;
        }
        if("SETTLED".equals(current) || "FAILED".equals(current)) return;
        String chainStatus=switch(decision.kind()) {case "CONFIRMED" -> "CONFIRMED";case "FAILED" -> "MINED_REVERTED";default -> "MINED_SUCCESS";};
        jdbc.update("UPDATE chain_transactions SET status=?,included_block_number=?,confirmations=?,mined_at=COALESCE(mined_at,now()),confirmed_at=CASE WHEN ?='CONFIRMED' THEN COALESCE(confirmed_at,now()) ELSE confirmed_at END,last_error=NULL WHERE payout_id=?",
            chainStatus,receipt.blockNumber(),decision.confirmations(),chainStatus,expected.payoutId());
        if("REQUESTED".equals(current) && !"FAILED".equals(decision.kind())) {
            transition(expected.payoutId(),current,"AWAITING_CONFIRMATION","TRANSACTION_OBSERVED");current="AWAITING_CONFIRMATION";
        }
        String target=switch(decision.kind()) {case "CONFIRMED" -> "SETTLED";case "FAILED" -> "FAILED";default -> "AWAITING_CONFIRMATION";};
        transition(expected.payoutId(),current,target,decision.reason());touch(expected.payoutId());
    }
}
