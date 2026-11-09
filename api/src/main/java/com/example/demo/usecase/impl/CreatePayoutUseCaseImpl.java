package com.example.demo.usecase.impl;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.payout.application.PayoutService;
import com.example.demo.payout.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CreatePayoutUseCaseImpl implements CreatePayoutUseCase {
    private final PayoutService service;
    public CreatePayoutUseCaseImpl(PayoutService service) { this.service = service; }
    @Override @Transactional
    public PayoutAppResponse create(CreatePayoutAppRequest input) {
        var payout = service.create(new PayoutRequest(input.merchantId(), new EthereumAddress(input.recipientAddress()), EthAmount.fromEth(input.amount()), input.idempotencyKey()));
        return new PayoutAppResponse(payout.id(), payout.merchantId(), payout.recipientAddress(),
            new EthAmount(new java.math.BigInteger(payout.amountAtomic())).toEth(), payout.status().name());
    }
}
