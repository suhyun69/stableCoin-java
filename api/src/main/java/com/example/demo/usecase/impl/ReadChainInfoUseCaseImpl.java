package com.example.demo.usecase.impl;

import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.common.exception.ServiceUnavailableException;
import com.example.demo.usecase.ReadChainInfoUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ReadChainInfoUseCaseImpl implements ReadChainInfoUseCase {
  private final EthChainPort chain;

  public ReadChainInfoUseCaseImpl(EthChainPort chain) {
    this.chain = chain;
  }

  public long chainId() {
    try {
      return chain.chainId();
    } catch (RuntimeException e) {
      throw new ServiceUnavailableException("chain is temporarily unavailable", e);
    }
  }
}
