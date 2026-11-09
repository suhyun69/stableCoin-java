package com.example.demo.outbox.adapter.out.persistence;
import com.example.demo.outbox.application.port.out.OutboxStorePort;
import com.example.demo.outbox.domain.OutboxMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public class OutboxPersistenceAdapter implements OutboxStorePort {
    private final JdbcTemplate jdbc;
    public OutboxPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<OutboxMessage> claim(String owner) {
        return jdbc.query("""
            WITH candidate AS (
              SELECT id FROM outbox_messages WHERE status='PENDING' AND available_at<=now()
              AND (locked_at IS NULL OR locked_at<now()-interval '30 seconds')
              ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            ) UPDATE outbox_messages m SET locked_at=now(),locked_by=?,attempts=attempts+1
              FROM candidate c WHERE m.id=c.id RETURNING m.*
            """, (rs,n) -> new OutboxMessage(rs.getString("id"),rs.getString("aggregate_id"),
                rs.getString("payload"),rs.getString("locked_by"),rs.getInt("attempts")), owner).stream().findFirst();
    }
    public boolean published(OutboxMessage message) {
        return jdbc.update("UPDATE outbox_messages SET status='PUBLISHED',published_at=now(),locked_at=NULL,locked_by=NULL,last_error=NULL WHERE id=? AND locked_by=? AND status='PENDING'",
            message.id(),message.owner()) == 1;
    }
    public void failed(OutboxMessage message, String error) {
        int delay = (int)Math.min(300, 1L << Math.min(8,message.attempts()));
        jdbc.update("UPDATE outbox_messages SET last_error=?,available_at=now()+make_interval(secs => ?),locked_at=NULL,locked_by=NULL WHERE id=? AND locked_by=? AND status='PENDING'",
            error.substring(0,Math.min(error.length(),2000)),delay,message.id(),message.owner());
    }
}
