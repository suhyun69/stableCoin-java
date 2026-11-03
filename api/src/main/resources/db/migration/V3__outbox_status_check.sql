-- outbox 메시지 상태: PENDING(발행 대기) → PUBLISHED(Kafka에 발행됨) 또는 FAILED(최대 시도 횟수 초과)
ALTER TABLE outbox_messages ADD CONSTRAINT outbox_messages_status_check
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));
