package com.example.demo.reconciliation.adapter.out.persistence;
import com.example.demo.reconciliation.application.port.out.ReconciliationStorePort;
import com.example.demo.reconciliation.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ReconciliationPersistenceAdapter implements ReconciliationStorePort {
    private final JdbcTemplate jdbc;
    public ReconciliationPersistenceAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void lock() {jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('stablepay.reconciliation',0))",rs -> {});}
    public List<ReconciliationEntry> ledger(BlockRange range,List<String> hashes,String sender) {
        List<Object> args=new ArrayList<>(List.of(sender.toLowerCase(Locale.ROOT),range.from(),range.to()));
        String matching=hashes.isEmpty()?"false":"ct.transaction_hash IN ("+String.join(",",Collections.nCopies(hashes.size(),"?"))+")";
        args.addAll(hashes);
        return jdbc.query("SELECT p.id,p.status payout_status,p.recipient_address,p.amount_atomic,ct.sender_address,ct.nonce,ct.transaction_hash,ct.included_block_number,ct.status chain_status,(p.amount_atomic=ct.amount_atomic AND lower(p.recipient_address)=lower(ct.recipient_address)) consistent FROM payouts p JOIN chain_transactions ct ON ct.payout_id=p.id WHERE lower(ct.sender_address)=? AND (ct.included_block_number BETWEEN ? AND ? OR "+matching+")",
            (rs,n) -> new ReconciliationEntry(rs.getString("id"),rs.getString("transaction_hash"),rs.getString("sender_address"),rs.getString("recipient_address"),rs.getBigDecimal("amount_atomic").toBigIntegerExact(),rs.getLong("nonce"),rs.getObject("included_block_number",Long.class),rs.getString("payout_status"),rs.getString("chain_status"),rs.getBoolean("consistent")),args.toArray());
    }
    public void save(ReconciliationFinding finding,boolean resolved) {
        List<String> existing=finding.payoutId()==null
            ?jdbc.queryForList("SELECT id FROM reconciliation_findings WHERE status='OPEN' AND payout_id IS NULL AND transaction_hash=? AND kind=? AND log_index IS NULL",String.class,finding.hash(),finding.kind())
            :jdbc.queryForList("SELECT id FROM reconciliation_findings WHERE status='OPEN' AND payout_id=? AND kind=?",String.class,finding.payoutId(),finding.kind());
        String status=resolved?"RESOLVED":"OPEN";
        if(existing.isEmpty()) jdbc.update("INSERT INTO reconciliation_findings (id,kind,payout_id,transaction_hash,detail,status,resolved_at) VALUES (?,?,?,?,jsonb_build_object('reason',?::text),?,CASE WHEN ? THEN now() ELSE NULL END)",
            "finding_"+UUID.randomUUID(),finding.kind(),finding.payoutId(),finding.hash(),finding.reason(),status,resolved);
        else jdbc.update("UPDATE reconciliation_findings SET detail=jsonb_build_object('reason',?::text),status=?,updated_at=now(),resolved_at=CASE WHEN ? THEN now() ELSE NULL END WHERE id=?",finding.reason(),status,resolved,existing.getFirst());
    }
    public void resolveAbsent(List<ReconciliationEntry> ledger,List<ObservedEthTransfer> observed,List<ReconciliationFinding> current) {
        Set<String> payouts=new HashSet<>();ledger.forEach(e -> payouts.add(e.payoutId()));
        Set<String> hashes=new HashSet<>();observed.forEach(e -> hashes.add(e.hash()));
        Set<String> keys=new HashSet<>();current.forEach(f -> keys.add(f.identity()));
        Set<String> kinds=Set.of("STATUS_LAG","MISSING_CHAIN_TRANSACTION","STATUS_MISMATCH","TRANSACTION_MISMATCH","CHAIN_TRANSACTION_WITHOUT_LEDGER","DUPLICATE_CHAIN_TRANSACTION");
        jdbc.query("SELECT id,kind,payout_id,transaction_hash FROM reconciliation_findings WHERE status='OPEN'",rs -> {
            String payout=rs.getString("payout_id"),hash=rs.getString("transaction_hash"),kind=rs.getString("kind");
            var finding=new ReconciliationFinding(kind,payout,hash,"");
            if(kinds.contains(kind) && (payouts.contains(payout)||hashes.contains(hash)) && !keys.contains(finding.identity()))
                jdbc.update("UPDATE reconciliation_findings SET status='RESOLVED',resolved_at=now(),updated_at=now() WHERE id=?",rs.getString("id"));
        });
    }
}
