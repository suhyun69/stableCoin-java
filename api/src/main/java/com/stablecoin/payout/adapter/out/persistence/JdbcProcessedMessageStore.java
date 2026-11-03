package com.stablecoin.payout.adapter.out.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.ProcessedMessagePort;

/**
 * processed_messages 테이블. (consumer_name, message_id) 기본 키로 같은 메시지를 한 번만 기록한다.
 * 같은 메시지를 동시에 받으면 늦은 쪽은 먼저 넣은 트랜잭션이 끝날 때까지 기다렸다가 아무것도 하지 않는다.
 */
@Component
class JdbcProcessedMessageStore implements ProcessedMessagePort {

	private final JdbcTemplate jdbc;

	JdbcProcessedMessageStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean markProcessed(String consumerName, String messageId, UUID payoutId) {
		return jdbc.update("""
				INSERT INTO processed_messages (consumer_name, message_id, payout_id) VALUES (?, ?, ?)
				ON CONFLICT DO NOTHING""", consumerName, messageId, payoutId) == 1;
	}

}
