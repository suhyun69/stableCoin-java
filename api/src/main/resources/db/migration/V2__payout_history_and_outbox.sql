-- 지급 저장과 "체인으로 보내라" 메시지(outbox)를 한 트랜잭션으로 저장하기 위한 스키마.
-- (stableCoin TS d5dd04b)

-- 1) payouts: 멱등성 키 중복 금지, 같은 키로 다른 내용이 오는지 비교할 fingerprint, 상태 값 제한
ALTER TABLE payouts ADD COLUMN request_fingerprint varchar(64) NOT NULL DEFAULT 'legacy';
ALTER TABLE payouts ALTER COLUMN request_fingerprint DROP DEFAULT;
ALTER TABLE payouts ADD CONSTRAINT payouts_idempotency_key_key UNIQUE (idempotency_key);
ALTER TABLE payouts ADD CONSTRAINT payouts_amount_wei_check CHECK (amount_wei > 0);
ALTER TABLE payouts ADD CONSTRAINT payouts_status_check
    CHECK (status IN ('REQUESTED', 'AWAITING_CONFIRMATION', 'SETTLED', 'FAILED', 'REQUIRES_REVIEW'));

-- 2) 지급 상태가 바뀔 때마다 한 줄씩 남긴다
CREATE TABLE payout_status_history (
    id          uuid        PRIMARY KEY,
    payout_id   uuid        NOT NULL REFERENCES payouts (id),
    from_status varchar(30),
    to_status   varchar(30) NOT NULL,
    reason      varchar(100) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX payout_status_history_payout_id_idx ON payout_status_history (payout_id);

-- 3) 나중에 처리할 일(체인 전송 등). 발행기가 PENDING 메시지를 읽어 처리한다.
--    aggregate_type 으로 여러 종류의 대상을 담을 수 있게 aggregate_id 에는 외래 키를 걸지 않는다.
CREATE TABLE outbox_messages (
    id             uuid         PRIMARY KEY,
    aggregate_type varchar(50)  NOT NULL,
    aggregate_id   uuid         NOT NULL,
    type           varchar(100) NOT NULL,
    payload        jsonb        NOT NULL,
    status         varchar(20)  NOT NULL DEFAULT 'PENDING',
    attempts       integer      NOT NULL DEFAULT 0,
    available_at   timestamptz  NOT NULL DEFAULT now(),
    locked_at      timestamptz,
    locked_by      varchar(100),
    published_at   timestamptz,
    last_error     text,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    -- 같은 대상에 같은 종류의 메시지는 하나만
    UNIQUE (aggregate_type, aggregate_id, type)
);

-- 발행기가 처리할 메시지를 빨리 찾기 위한 인덱스
CREATE INDEX outbox_messages_pending_idx ON outbox_messages (available_at) WHERE status = 'PENDING';
