-- 지급 요청.
-- idempotency_key 에는 일부러 UNIQUE 제약을 걸지 않았다. 같은 키로 재시도하면 지급이 중복되는 문제를 먼저 재현하기 위해서다.
CREATE TABLE payouts (
    id                uuid         PRIMARY KEY,
    merchant_id       varchar(64)  NOT NULL,
    recipient_address varchar(42)  NOT NULL,
    -- 최소 단위(wei) 정수. uint256 최댓값(78자리)까지 담는다.
    amount_wei        numeric(78, 0) NOT NULL,
    idempotency_key   varchar(255) NOT NULL,
    status            varchar(20)  NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now()
);
