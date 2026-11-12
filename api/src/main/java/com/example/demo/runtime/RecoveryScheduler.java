package com.example.demo.runtime;

import com.example.demo.usecase.RecoverBroadcastsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"stablepay.chain.enabled", "stablepay.workers.enabled"},
    havingValue = "true")
public class RecoveryScheduler {
  private final RecoverBroadcastsUseCase recover;

  public RecoveryScheduler(RecoverBroadcastsUseCase recover) {
    this.recover = recover;
  }

  @Scheduled(fixedDelayString = "${stablepay.workers.recovery-delay-ms:5000}")
  public void recover() {
    recover.recover();
  }
}
