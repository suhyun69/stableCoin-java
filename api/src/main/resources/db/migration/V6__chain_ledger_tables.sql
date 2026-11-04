-- 체인 원장과 대사 테이블 (stableCoin TS 최종본). 이 앱의 지급 흐름은 아직 payout_dispatches 를 쓰고, 아래 세 테이블에는 쓰지 않는다.
-- TS DDL과 다른 점: payouts.id 가 uuid 라서 payout_id 를 uuid 로 맞췄고, id 도 다른 테이블처럼 uuid 로 했다.
-- processed_messages 는 V5 에 이미 있어서 만들지 않는다.

-- 지급마다 체인 트랜잭션 한 건 (의도 → 서명 → 전송 → 블록 포함 → 확정)
CREATE TABLE chain_transactions (
    id                    uuid           PRIMARY KEY,
    payout_id             uuid           NOT NULL UNIQUE REFERENCES payouts (id),
    sender_address        text           NOT NULL,
    recipient_address     text           NOT NULL,
    amount_atomic         numeric(78, 0) NOT NULL CHECK (amount_atomic > 0),
    nonce                 bigint         NOT NULL,
    status                text           NOT NULL CHECK (status IN
        ('CREATED', 'SIGNED', 'BROADCAST_UNKNOWN', 'PENDING',
         'MINED_SUCCESS', 'MINED_REVERTED', 'CONFIRMED', 'FAILED')),
    transaction_hash      text           UNIQUE,
    raw_transaction       text,
    last_error            text,
    included_block_number bigint,
    confirmations         bigint         NOT NULL DEFAULT 0,
    created_at            timestamptz    NOT NULL DEFAULT now(),
    signed_at             timestamptz,
    broadcast_at          timestamptz,
    mined_at              timestamptz,
    confirmed_at          timestamptz,
    UNIQUE (sender_address, nonce)
);

-- 지갑별로 다음에 쓸 nonce
CREATE TABLE chain_nonce_allocations (
    sender_address text   PRIMARY KEY,
    next_nonce     bigint NOT NULL CHECK (next_nonce >= 0)
);

ALTER TABLE payouts ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

-- 장부와 체인을 비교해 찾은 불일치. 같은 문제는 OPEN 인 동안 한 줄만 남는다.
CREATE TABLE reconciliation_findings (
    id               uuid        PRIMARY KEY,
    kind             text        NOT NULL,
    payout_id        uuid        REFERENCES payouts (id),
    transaction_hash text,
    log_index        integer,
    detail           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    status           text        NOT NULL DEFAULT 'OPEN',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    resolved_at      timestamptz,
    CONSTRAINT reconciliation_finding_status_check CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX reconciliation_findings_open_idx
    ON reconciliation_findings (created_at)
    WHERE status = 'OPEN';

CREATE UNIQUE INDEX reconciliation_findings_open_payout_kind_idx
    ON reconciliation_findings (payout_id, kind)
    WHERE status = 'OPEN' AND payout_id IS NOT NULL;

CREATE UNIQUE INDEX reconciliation_findings_open_event_kind_idx
    ON reconciliation_findings (transaction_hash, log_index, kind)
    WHERE status = 'OPEN' AND transaction_hash IS NOT NULL AND log_index IS NOT NULL;
