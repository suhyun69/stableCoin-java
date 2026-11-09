package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI demoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Demo API")
                .version("v1")
                .description("""
                        할 일(Todo) 관리 API.

                        구조와 설계 규칙은 저장소 루트의 architecture.md 를 참고한다.
                        요청/응답은 WebRequest → AppRequest → 도메인 → AppResponse → WebResponse 순으로 변환된다.
                        """)
                .license(new License().name("Unlicensed")));
    }
}
