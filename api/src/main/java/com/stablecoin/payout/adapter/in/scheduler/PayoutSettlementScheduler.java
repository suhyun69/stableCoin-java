package com.stablecoin.payout.adapter.in.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.stablecoin.payout.application.port.in.ConfirmPayoutsUseCase;
import com.stablecoin.payout.application.port.in.DispatchPayoutUseCase;

/**
 * payout.confirmation.interval 마다 (1) 노드에 보낸 지급의 영수증을 확인해 정산하고, (2) 중간에 멈춘 전송을 이어간다.
 * payout.dispatcher.enabled=false 면 등록되지 않는다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "payout.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
class PayoutSettlementScheduler {

	private static final Logger log = LoggerFactory.getLogger(PayoutSettlementScheduler.class);

	private final ConfirmPayoutsUseCase confirmPayouts;

	private final DispatchPayoutUseCase dispatchPayout;

	PayoutSettlementScheduler(ConfirmPayoutsUseCase confirmPayouts, DispatchPayoutUseCase dispatchPayout) {
		this.confirmPayouts = confirmPayouts;
		this.dispatchPayout = dispatchPayout;
	}

	@Scheduled(fixedDelayString = "${payout.confirmation.interval:2s}",
			initialDelayString = "${payout.confirmation.interval:2s}")
	void run() {
		try {
			confirmPayouts.confirmBroadcast();
			dispatchPayout.resumeStuck();
		}
		catch (RuntimeException ex) {
			// 노드나 DB에 연결할 수 없는 경우 등. 다음 주기에 다시 시도한다.
			log.warn("Payout settlement run failed: {}", ex.toString());
		}
	}

}
