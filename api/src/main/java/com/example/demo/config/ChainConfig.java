package com.example.demo.config;

import com.example.demo.chain.adapter.out.rpc.AnvilWalletRegistry;
import com.example.demo.chain.adapter.out.rpc.Web3jEthAdapter;
import com.example.demo.chain.application.port.out.EthChainPort;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
public class ChainConfig {
  @Bean(destroyMethod = "shutdown")
  Web3j web3j(@Value("${stablepay.chain.rpc-url}") String url) {
    var http =
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(5))
            .callTimeout(Duration.ofSeconds(8))
            .build();
    return Web3j.build(new HttpService(url, http));
  }

  @Bean
  EthChainPort ethChain(
      Web3j rpc, AnvilWalletRegistry wallets, @Value("${stablepay.chain.id}") long chainId) {
    var chain = new Web3jEthAdapter(rpc, wallets, chainId);
    chain.validateAnvilAccounts();
    return chain;
  }
}
