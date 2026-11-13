package com.example.demo.usecase.dto;

import java.util.List;

public record WalletsAppResponse(
    long chainId, String blockNumber, List<WalletAppResponse> wallets) {
  public record WalletAppResponse(String address, String balanceEth, String balanceWei) {}
}
