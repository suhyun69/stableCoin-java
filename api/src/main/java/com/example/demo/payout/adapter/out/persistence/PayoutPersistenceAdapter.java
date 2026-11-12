package com.example.demo.payout.adapter.out.persistence;

import com.example.demo.common.exception.ConflictException;
import com.example.demo.payout.application.port.out.PayoutRepositoryPort;
import com.example.demo.payout.domain.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayoutPersistenceAdapter implements PayoutRepositoryPort {
  private final JdbcTemplate jdbc;

  public PayoutPersistenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Payout createRequested(PayoutRequest request) {
    // Lock before read: simultaneous identical requests return the same committed payout.
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, request.idempotencyKey());
    var existing =
        jdbc.query(
            "SELECT * FROM payouts WHERE idempotency_key = ?",
            (rs, n) ->
                new Payout(
                    rs.getString("id"),
                    rs.getString("merchant_id"),
                    rs.getString("recipient_address"),
                    rs.getBigDecimal("amount_atomic").toBigIntegerExact().toString(),
                    rs.getString("request_fingerprint"),
                    PayoutStatus.valueOf(rs.getString("status"))),
            request.idempotencyKey());
    if (!existing.isEmpty()) {
      var payout = existing.getFirst();
      if (!payout.requestFingerprint().equals(request.fingerprint()))
        throw new ConflictException("Idempotency-Key was reused with a different request");
      return payout;
    }
    String id = "pay_" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payouts (id,idempotency_key,merchant_id,recipient_address,amount_atomic,request_fingerprint,status) VALUES (?,?,?,?,?,?,'REQUESTED')",
        id,
        request.idempotencyKey(),
        request.merchantId(),
        request.recipientAddress().value(),
        new BigDecimal(request.amount().wei()),
        request.fingerprint());
    jdbc.update(
        "INSERT INTO payout_status_history (id,payout_id,to_status,reason) VALUES (?,?,'REQUESTED','PAYOUT_CREATED')",
        "hist_" + UUID.randomUUID(),
        id);
    String eventId = "out_" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO outbox_messages (id,aggregate_type,aggregate_id,type,payload) VALUES (?,'PAYOUT',?,'PAYOUT_REQUESTED',jsonb_build_object('eventId',?::text,'eventType','PAYOUT_REQUESTED','schemaVersion',1,'payoutId',?::text))",
        eventId,
        id,
        eventId,
        id);
    return new Payout(
        id,
        request.merchantId(),
        request.recipientAddress().value(),
        request.amount().wei().toString(),
        request.fingerprint(),
        PayoutStatus.REQUESTED);
  }
}
