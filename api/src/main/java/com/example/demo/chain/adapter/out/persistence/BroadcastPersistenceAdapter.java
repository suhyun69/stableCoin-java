package com.example.demo.chain.adapter.out.persistence;
import com.example.demo.chain.application.port.out.BroadcastStorePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public class BroadcastPersistenceAdapter implements BroadcastStorePort {
    private final JdbcTemplate jdbc;
    public BroadcastPersistenceAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public boolean processed(String eventId,String payoutId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM processed_messages WHERE consumer_name='payout-requested' AND message_id=? AND payout_id=?)",Boolean.class,eventId,payoutId));
    }
    public void validateEvent(String eventId,String payoutId) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM outbox_messages WHERE id=? AND aggregate_id=? AND type='PAYOUT_REQUESTED')",Boolean.class,eventId,payoutId)))
            throw new IllegalArgumentException("event does not match persisted payout outbox");
    }
    public void complete(String eventId,String payoutId,boolean accepted,String error) {
        var rows=jdbc.queryForList("SELECT p.status payout_status,ct.status chain_status FROM payouts p JOIN chain_transactions ct ON ct.payout_id=p.id WHERE p.id=? FOR UPDATE OF p,ct",payoutId);
        if(rows.isEmpty()) throw new IllegalStateException("prepared transaction missing");
        var row=rows.getFirst(); String chainStatus=(String)row.get("chain_status");
        if("SIGNED".equals(chainStatus) || "BROADCAST_UNKNOWN".equals(chainStatus)) {
            jdbc.update("UPDATE chain_transactions SET status=?,broadcast_at=COALESCE(broadcast_at,now()),last_error=? WHERE payout_id=?",
                accepted?"PENDING":"BROADCAST_UNKNOWN",error,payoutId);
        }
        if("REQUESTED".equals(row.get("payout_status"))) {
            jdbc.update("UPDATE payouts SET status='AWAITING_CONFIRMATION',updated_at=now() WHERE id=?",payoutId);
            jdbc.update("INSERT INTO payout_status_history (id,payout_id,from_status,to_status,reason) VALUES (?,?,'REQUESTED','AWAITING_CONFIRMATION',?)",
                "hist_"+UUID.randomUUID(),payoutId,accepted?"TRANSACTION_BROADCAST":"BROADCAST_UNCERTAIN");
        }
        if(eventId!=null) jdbc.update("INSERT INTO processed_messages (consumer_name,message_id,payout_id) VALUES ('payout-requested',?,?) ON CONFLICT DO NOTHING",eventId,payoutId);
    }
}
