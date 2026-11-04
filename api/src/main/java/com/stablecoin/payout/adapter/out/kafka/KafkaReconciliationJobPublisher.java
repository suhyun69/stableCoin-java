package com.stablecoin.payout.adapter.out.kafka;

import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.ReconciliationJobPort;

/**
 * 대사 작업을 stablecoin.reconciliation 토픽에 넣는다. Kafka가 받을 때까지 기다려, 실패하면 호출한 쪽이 알 수 있게 한다.
 */
@Component
class KafkaReconciliationJobPublisher implements ReconciliationJobPort {

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final JsonMapper jsonMapper;

	private final String topic;

	KafkaReconciliationJobPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
			@Value("${outbox.kafka.topic-prefix}") String topicPrefix) {
		this.kafkaTemplate = kafkaTemplate;
		this.jsonMapper = jsonMapper;
		this.topic = topicPrefix + "reconciliation";
	}

	@Override
	public void enqueue(long fromBlock, long toBlock) {
		String job = jsonMapper.writeValueAsString(Map.of("fromBlock", fromBlock, "toBlock", toBlock));
		kafkaTemplate.send(topic, fromBlock + "-" + toBlock, job).join();
	}

}
