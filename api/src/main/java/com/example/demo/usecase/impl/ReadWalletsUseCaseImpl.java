package com.example.demo.usecase.impl;

import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.common.exception.ServiceUnavailableException;
import com.example.demo.usecase.ReadWalletsUseCase;
import com.example.demo.usecase.dto.WalletsAppResponse;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ReadWalletsUseCaseImpl implements ReadWalletsUseCase {
  private final EthChainPort chain;

  public ReadWalletsUseCaseImpl(EthChainPort chain) {
    this.chain = chain;
  }

  public WalletsAppResponse read() {
    try {
      long chainId = chain.chainId();
      long block = chain.head();
      var wallets =
          chain.senders().stream()
              .sorted()
              .map(
                  address -> {
                    var wei = chain.balance(address, block);
                    return new WalletsAppResponse.WalletAppResponse(
                        address,
                        new BigDecimal(wei, 18).stripTrailingZeros().toPlainString(),
                        wei.toString());
                  })
              .toList();
      return new WalletsAppResponse(chainId, Long.toString(block), wallets);
    } catch (RuntimeException e) {
      throw new ServiceUnavailableException("wallet balances unavailable", e);
    }
  }
}
