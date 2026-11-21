package com.commicom.itda.infra.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 호출마다 모델·토큰 수·걸린 시간·종료 이유를 로그로 남긴다.
 * 서버를 켠 뒤 누적 합계도 함께 찍어서, 청구서(하루 늦게 반영)를 기다리지 않고 사용량을 볼 수 있다.
 * 로그 예) [AI 사용량] global.anthropic.claude-haiku-5-5 | 입력 3,412 · 출력 1,208 토큰 | 4.2초 | end_turn | 누적 3회 · 입력 9,800 · 출력 3,900
 */
@Slf4j
public class TokenUsageLogAdvisor implements CallAdvisor {

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong totalInput = new AtomicLong();
    private final AtomicLong totalOutput = new AtomicLong();

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        try {
            log(response.chatResponse(), requestedModel(request), seconds);
        } catch (Exception e) {
            log.debug("[AI 사용량] 기록 실패: {}", e.getMessage()); // 로그 때문에 본 요청이 실패하면 안 됨
        }
        return response;
    }

    // Bedrock 응답에는 모델 이름이 비어 있어서, 요청에 넣은 모델 이름을 대신 쓴다
    private static String requestedModel(ChatClientRequest request) {
        var options = request.prompt().getOptions();
        return options == null || options.getModel() == null ? "?" : options.getModel();
    }

    private void log(ChatResponse chat, String requestedModel, double seconds) {
        if (chat == null || chat.getMetadata() == null) {
            log.info("[AI 사용량] 응답 메타데이터 없음 | {}초", String.format("%.1f", seconds));
            return;
        }
        Usage usage = chat.getMetadata().getUsage();
        long in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        long out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        String finish = chat.getResult() == null || chat.getResult().getMetadata() == null
                ? "?" : String.valueOf(chat.getResult().getMetadata().getFinishReason());

        long n = calls.incrementAndGet();
        long sumIn = totalInput.addAndGet(in);
        long sumOut = totalOutput.addAndGet(out);
        String model = chat.getMetadata().getModel();
        log.info("[AI 사용량] {} | 입력 {} · 출력 {} 토큰 | {}초 | {} | 누적 {}회 · 입력 {} · 출력 {}",
                model == null || model.isBlank() ? requestedModel : model, fmt(in), fmt(out), String.format("%.1f", seconds), finish,
                n, fmt(sumIn), fmt(sumOut));
    }

    private static String fmt(long n) {
        return String.format("%,d", n);
    }

    @Override
    public String getName() {
        return "TokenUsageLogAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
