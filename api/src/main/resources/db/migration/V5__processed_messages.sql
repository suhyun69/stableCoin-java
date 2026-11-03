-- 소비자 inbox: 소비자가 처리한 메시지를 소비자별로 한 번만 기록한다 (stableCoin TS processed_messages).
-- 지급 소비자는 이 기록과 전송 기록(payout_dispatches STARTED)을 같은 트랜잭션으로 남긴다.
-- 같은 메시지를 다시 받으면 건너뛰고, 그 사이 멈춘 전송은 재개 작업이 이어간다.
CREATE TABLE processed_messages (
    consumer_name varchar(100) NOT NULL,
    message_id    varchar(100) NOT NULL,
    payout_id     uuid         NOT NULL REFERENCES payouts (id),
    processed_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, message_id)
);
