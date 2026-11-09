package com.example.demo.runtime;
import com.example.demo.usecase.ConfirmPayoutsUseCase;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
@Component
@ConditionalOnProperty(name={"stablepay.chain.enabled","stablepay.workers.enabled"},havingValue="true")
public class ConfirmationScheduler {
    private final ConfirmPayoutsUseCase confirm;
    public ConfirmationScheduler(ConfirmPayoutsUseCase confirm) {this.confirm=confirm;}
    @Scheduled(fixedDelayString="${stablepay.workers.confirm-delay-ms:2000}")
    public void confirm() {confirm.confirm();}
}
