package com.commicom.itda.global.config;

import com.commicom.itda.infra.ai.TokenUsageLogAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class AiConfig {

    @Bean
    @Lazy
    public ChatClient chatClient(ChatClient.Builder builder) {
        // 모든 AI 호출의 토큰 사용량을 로그로 남김 ([AI 사용량] 으로 검색)
        return builder.defaultAdvisors(new TokenUsageLogAdvisor()).build();
    }
}
