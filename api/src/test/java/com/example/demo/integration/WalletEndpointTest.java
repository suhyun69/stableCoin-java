package com.example.demo.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.demo.chain.adapter.out.rpc.*;
import com.example.demo.controller.WalletController;
import com.example.demo.usecase.impl.ReadWalletsUseCaseImpl;
import java.math.BigInteger;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.http.HttpService;

@Tag("chain")
class WalletEndpointTest {
  @Test
  void readsAnvilBalancesThroughEndpoint() throws Exception {
    var rpc =
        Web3j.build(
            new HttpService(
                System.getenv().getOrDefault("ANVIL_RPC_URL", "http://localhost:8545")));
    try {
      var wallets = new AnvilWalletRegistry(EthRpcTest.KEY);
      var chain = new Web3jEthAdapter(rpc, wallets, 31337);
      chain.validateAnvilAccounts();
      String address = wallets.addresses().iterator().next();
      var mvc =
          MockMvcBuilders.standaloneSetup(new WalletController(new ReadWalletsUseCaseImpl(chain)))
              .build();
      var result =
          mvc.perform(get("/wallets"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.wallets.length()").value(1))
              .andExpect(jsonPath("$.wallets[0].address").value(address))
              .andReturn();
      var json =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readTree(result.getResponse().getContentAsString());
      var block = DefaultBlockParameter.valueOf(new BigInteger(json.get("blockNumber").asText()));
      org.junit.jupiter.api.Assertions.assertEquals(
          rpc.ethGetBalance(address, block).send().getBalance().toString(),
          json.path("wallets").get(0).get("balanceWei").asText());
    } finally {
      rpc.shutdown();
    }
  }
}
