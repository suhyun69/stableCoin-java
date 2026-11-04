package com.stablecoin.payout.adapter.in.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.in.RunReconciliationUseCase;

/**
 * stablecoin.reconciliation 토픽에서 대사 작업을 받아 실행한다. 체인이 아직 끝 블록에 이르지 않았거나 노드에 연결할 수 없으면
 * KafkaConsumerConfig 의 분류에 따라 다시 받고, 해석할 수 없는 작업은 DLT로 보낸다.
 */
@Component
class ReconciliationJobListener {

	private final RunReconciliationUseCase reconciliation;

	private final JsonMapper jsonMapper;

	ReconciliationJobListener(RunReconciliationUseCase reconciliation, JsonMapper jsonMapper) {
		this.reconciliation = reconciliation;
		this.jsonMapper = jsonMapper;
	}

	@KafkaListener(id = "reconciliation", topics = "${outbox.kafka.topic-prefix}reconciliation",
			groupId = "${payout.reconciliation.group-id:stablecoin-reconciliation}",
			autoStartup = "${payout.dispatcher.enabled:true}")
	void onMessage(ConsumerRecord<String, String> record) {
		long fromBlock;
		long toBlock;
		try {
			JsonNode job = jsonMapper.readTree(record.value());
			fromBlock = job.get("fromBlock").asLong();
			toBlock = job.get("toBlock").asLong();
		}
		catch (RuntimeException ex) {
			throw new InvalidKafkaMessageException("invalid reconciliation job at " + record.topic() + "-"
					+ record.partition() + "@" + record.offset() + ": " + ex, ex);
		}
		reconciliation.run(fromBlock, toBlock);
	}

}
