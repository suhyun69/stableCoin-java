package com.example.demo.usecase.impl;

import com.example.demo.chain.application.port.out.WalletRegistryPort;
import com.example.demo.payout.application.PayoutService;
import com.example.demo.payout.domain.*;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatePayoutUseCaseImpl implements CreatePayoutUseCase {
  private final PayoutService service;

  private final WalletRegistryPort wallets;

  public CreatePayoutUseCaseImpl(PayoutService service, WalletRegistryPort wallets) {
    this.wallets = wallets;
    this.service = service;
  }

  @Override
  @Transactional
  public PayoutAppResponse create(CreatePayoutAppRequest input) {
    var from = new EthereumAddress(input.from());
    var to = new EthereumAddress(input.to());
    wallets.requireRegistered(from.value());
    wallets.requireRegistered(to.value());
    var payout =
        service.create(
            new PayoutRequest(
                input.merchantId(),
                from,
                to,
                EthAmount.fromEth(input.amount()),
                input.idempotencyKey()));
    return new PayoutAppResponse(
        payout.id(),
        payout.merchantId(),
        payout.senderAddress(),
        payout.recipientAddress(),
        new EthAmount(new java.math.BigInteger(payout.amountAtomic())).toEth(),
        payout.status().name());
  }
}
