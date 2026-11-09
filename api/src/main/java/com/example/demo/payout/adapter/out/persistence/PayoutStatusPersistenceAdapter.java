package com.example.demo.payout.adapter.out.persistence;
import com.example.demo.payout.domain.PayoutStatusView;
import com.example.demo.payout.application.port.out.PayoutStatusReaderPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public class PayoutStatusPersistenceAdapter implements PayoutStatusReaderPort {
    private final JdbcTemplate jdbc;
    public PayoutStatusPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<PayoutStatusView> findById(String id) {
        return jdbc.query("SELECT p.id,p.status,ct.transaction_hash,ct.nonce,ct.status chain_status,ct.confirmations FROM payouts p LEFT JOIN chain_transactions ct ON ct.payout_id=p.id WHERE p.id=?",
            (rs,n) -> new PayoutStatusView(rs.getString("id"), rs.getString("status"), rs.getString("chain_status") == null ? null :
            new PayoutStatusView.Chain(rs.getString("transaction_hash"),rs.getString("nonce"),rs.getString("chain_status"),rs.getString("confirmations"))), id).stream().findFirst();
    }
}
