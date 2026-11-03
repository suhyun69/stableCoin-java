-- 지급 전송 기록. 지급마다 한 행만 만들 수 있어(payout_id UNIQUE) 같은 지급을 두 번 보내지 않는다.
-- (stableCoin TS payout_dispatches 를 바탕으로, 서명한 트랜잭션을 보내기 전에 저장해 중간에 죽어도 이어서 보낼 수 있게 확장)
--
--   STARTED   소비자가 이 지급의 전송을 맡음 (아직 서명 전)
--   SIGNED    서명한 트랜잭션(signed_tx, tx_hash, nonce)을 저장함. 노드에 보냈는지는 아직 모름
--   BROADCAST 노드가 트랜잭션을 받음. 블록 확정을 기다림
--   CONFIRMED 블록에서 성공함 (지급 SETTLED)
--   FAILED    노드가 거부했거나 블록에서 실패함 (지급 FAILED)
--   REQUIRES_REVIEW 오래 확정되지 않는 등 결과를 알 수 없음 (지급 REQUIRES_REVIEW)
CREATE TABLE payout_dispatches (
    id           uuid        PRIMARY KEY,
    payout_id    uuid        NOT NULL UNIQUE REFERENCES payouts (id),
    status       varchar(20) NOT NULL CHECK (status IN
        ('STARTED', 'SIGNED', 'BROADCAST', 'CONFIRMED', 'FAILED', 'REQUIRES_REVIEW')),
    from_address varchar(42),
    nonce        bigint,
    tx_hash      varchar(66),
    signed_tx    text,
    last_error   text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

-- 확정 확인 작업이 BROADCAST 를 빨리 찾기 위한 인덱스
CREATE INDEX payout_dispatches_status_idx ON payout_dispatches (status, updated_at);

-- 지급에 체인 트랜잭션 해시를 남긴다
ALTER TABLE payouts ADD COLUMN transaction_hash varchar(66);

-- V1 의 varchar(20) 으로는 'AWAITING_CONFIRMATION'(21자)을 저장할 수 없어 늘린다
ALTER TABLE payouts ALTER COLUMN status TYPE varchar(30);
