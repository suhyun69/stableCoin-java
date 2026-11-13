package com.example.demo.controller;

import com.example.demo.controller.dto.WalletsWebResponse;
import com.example.demo.usecase.ReadWalletsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
public class WalletController {
  private final ReadWalletsUseCase wallets;

  public WalletController(ReadWalletsUseCase wallets) {
    this.wallets = wallets;
  }

  @GetMapping("/wallets")
  public WalletsWebResponse read() {
    var result = wallets.read();
    return new WalletsWebResponse(
        result.chainId(),
        result.blockNumber(),
        result.wallets().stream()
            .map(
                w ->
                    new WalletsWebResponse.WalletWebResponse(
                        w.address(), w.balanceEth(), w.balanceWei()))
            .toList());
  }
}
