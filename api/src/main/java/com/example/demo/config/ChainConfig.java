package com.example.demo.config;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.adapter.out.rpc.Web3jEthAdapter;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import okhttp3.OkHttpClient;
import java.time.Duration;
@Configuration
@ConditionalOnProperty(name="stablepay.chain.enabled",havingValue="true")
public class ChainConfig {
    @Bean(destroyMethod="shutdown") Web3j web3j(@Value("${stablepay.chain.rpc-url}") String url) {
        var http=new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(5)).callTimeout(Duration.ofSeconds(8)).build();
        return Web3j.build(new HttpService(url,http));
    }
    @Bean EthChainPort ethChain(Web3j rpc,@Value("${stablepay.chain.private-key}") String key,
            @Value("${stablepay.chain.id}") long chainId,@Value("${stablepay.chain.sender-address}") String sender) {
        var chain=new Web3jEthAdapter(rpc,key,chainId);
        if(!chain.sender().equalsIgnoreCase(sender)) throw new IllegalArgumentException("configured sender does not match private key");
        return chain;
    }
}
