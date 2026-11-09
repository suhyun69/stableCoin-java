package com.example.demo.chain.adapter.out.persistence;
import com.example.demo.chain.application.port.out.ChainTransactionStorePort;
import com.example.demo.chain.domain.*;
import com.example.demo.common.exception.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ChainTransactionPersistenceAdapter implements ChainTransactionStorePort {
    private final JdbcTemplate jdbc;
    public ChainTransactionPersistenceAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private final RowMapper<ChainTransaction> mapper=(rs,n) -> new ChainTransaction(rs.getString("id"),rs.getString("payout_id"),
        rs.getString("sender_address"),rs.getString("recipient_address"),rs.getBigDecimal("amount_atomic").toBigIntegerExact(),
        rs.getLong("nonce"),rs.getString("status"),rs.getString("raw_transaction"),rs.getString("transaction_hash"),
        rs.getObject("included_block_number",Long.class),rs.getLong("confirmations"));
    public Optional<ChainTransaction> find(String payoutId) {
        return jdbc.query("SELECT * FROM chain_transactions WHERE payout_id=?",mapper,payoutId).stream().findFirst();
    }
    public ChainTransaction prepare(String payoutId,String sender,long initialNonce) {
        var payouts=jdbc.queryForList("SELECT * FROM payouts WHERE id=? FOR UPDATE",payoutId);
        if(payouts.isEmpty()) throw new NotFoundException("payout not found");
        var existing=find(payoutId);
        if(existing.isPresent()) return existing.get();
        var payout=payouts.getFirst();
        if(!"REQUESTED".equals(payout.get("status"))) throw new ConflictException("payout cannot be sent");
        jdbc.update("INSERT INTO chain_nonce_allocations (sender_address,next_nonce) VALUES (?,?) ON CONFLICT DO NOTHING",sender,initialNonce);
        Long nonce=jdbc.queryForObject("UPDATE chain_nonce_allocations SET next_nonce=GREATEST(next_nonce,?)+1 WHERE sender_address=? RETURNING next_nonce-1",Long.class,initialNonce,sender);
        jdbc.update("INSERT INTO chain_transactions (id,payout_id,sender_address,recipient_address,amount_atomic,nonce,status) VALUES (?,?,?,?,?,?,'CREATED')",
            "ctx_"+UUID.randomUUID(),payoutId,sender,payout.get("recipient_address"),payout.get("amount_atomic"),nonce);
        return find(payoutId).orElseThrow();
    }
    public ChainTransaction signed(String payoutId,SignedEthTransaction signed) {
        jdbc.update("UPDATE chain_transactions SET raw_transaction=?,transaction_hash=?,status='SIGNED',signed_at=now() WHERE payout_id=? AND status='CREATED'",
            signed.raw(),signed.hash(),payoutId);
        // Always reload: another signer may have won the compare-and-set.
        return find(payoutId).orElseThrow();
    }
}
