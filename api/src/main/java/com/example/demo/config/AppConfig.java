package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** 시간 의존성을 주입 가능하게 해 테스트에서 고정 시각을 쓸 수 있도록 한다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
