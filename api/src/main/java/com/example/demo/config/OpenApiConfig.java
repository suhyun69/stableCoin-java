package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stablepayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("StablePay ETH Payout API")
                .version("v1")
                .description("""
                        Anvil 로컬 네트워크의 ETH 지급·정산·대사 API.

                        구조와 설계 규칙은 api/architecture.md 를 참고한다.
                        요청/응답은 WebRequest → AppRequest → 도메인 → AppResponse → WebResponse 순으로 변환된다.
                        """)
                .license(new License().name("Unlicensed")));
    }
}
