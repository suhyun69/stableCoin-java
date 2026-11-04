-- 서명 이후의 전송 상태를 payout_dispatches 에서 chain_transactions 로 옮긴다 (최종 ERD).
-- payout_dispatches 에는 소비자가 지급을 맡았다는 STARTED 표시만 남는다.

-- 서명한 적 있는 전송 기록만 옮긴다 (nonce 가 없으면 체인에 낼 트랜잭션도 없다).
-- 노드를 재시작하며 같은 지갑의 nonce 가 다시 쓰인 기록은 UNIQUE (sender_address, nonce) 때문에 함께 둘 수 없어 최신 행만 남긴다.
INSERT INTO chain_transactions (id, payout_id, sender_address, recipient_address, amount_atomic, nonce, status,
                                transaction_hash, raw_transaction, last_error, created_at, signed_at, broadcast_at,
                                confirmed_at)
SELECT d.id, d.payout_id, d.from_address, p.recipient_address, p.amount_wei, d.nonce,
       CASE d.status
           WHEN 'SIGNED' THEN 'SIGNED'
           WHEN 'BROADCAST' THEN 'PENDING'
           WHEN 'CONFIRMED' THEN 'CONFIRMED'
           WHEN 'FAILED' THEN 'MINED_REVERTED'
           -- 사람이 확인 중인 지급. 체인 결과는 아직 모른다
           WHEN 'REQUIRES_REVIEW' THEN 'PENDING'
       END,
       d.tx_hash, d.signed_tx, d.last_error, d.created_at, d.updated_at,
       CASE WHEN d.status <> 'SIGNED' THEN d.updated_at END,
       CASE WHEN d.status = 'CONFIRMED' THEN d.updated_at END
FROM payout_dispatches d
JOIN payouts p ON p.id = d.payout_id
WHERE d.nonce IS NOT NULL
ORDER BY d.updated_at DESC
ON CONFLICT DO NOTHING;

DROP INDEX payout_dispatches_status_idx;

ALTER TABLE payout_dispatches DROP CONSTRAINT payout_dispatches_status_check;
UPDATE payout_dispatches SET status = 'STARTED';
ALTER TABLE payout_dispatches ADD CONSTRAINT payout_dispatches_status_check CHECK (status = 'STARTED');

ALTER TABLE payout_dispatches
    DROP COLUMN from_address,
    DROP COLUMN nonce,
    DROP COLUMN tx_hash,
    DROP COLUMN signed_tx,
    DROP COLUMN last_error,
    DROP COLUMN updated_at;

ALTER TABLE payouts DROP COLUMN transaction_hash;

-- 확정 확인 작업이 기다리는 트랜잭션을 빨리 찾기 위한 인덱스
CREATE INDEX chain_transactions_status_idx ON chain_transactions (status);
