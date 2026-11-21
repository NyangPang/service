package com.commicom.itda.domain.generation.storyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴 데이터 → 장면 JSON (A 파트).
 * LLM 호출 → 검증 → 실패하면 오류 목록을 붙여 최대 2회 재요청. 그래도 실패하면 예외 — B 파트로 넘기지 않는다.
 */
@Slf4j
@Lazy
@Service
@RequiredArgsConstructor
public class StoryboardGenerator {

    static final int MAX_RETRIES = 2;

    private final ChatClient chatClient;
    private final StoryboardPrompt prompt;
    private final StoryboardValidator validator;

    public Result generate(MenuData menu, String appeal) {
        return generate(menu, appeal, null);
    }

    /** @param address 가게 주소 — 제목에 동네 이름을 넣을 때 쓴다 (없으면 null) */
    public Result generate(MenuData menu, String appeal, String address) {
        String original = prompt.build(menu, appeal, address);
        String request = original;
        List<Attempt> attempts = new ArrayList<>();

        for (int i = 0; i <= MAX_RETRIES; i++) {
            log.info("[스토리보드] {} 시도 {}/{}", menu.storeName(), i + 1, MAX_RETRIES + 1);
            String raw = chatClient.prompt().user(request).call().content();

            List<String> errors;
            Storyboard parsed = null;
            try {
                parsed = StoryboardJson.parseStoryboard(raw);
                errors = validator.validate(parsed, menu);
            } catch (Exception e) {
                errors = List.of("JSON으로 읽을 수 없습니다: " + e.getMessage()
                        + " — 장면 JSON 객체 하나만, 코드블록이나 설명 없이 출력하세요.");
            }
            attempts.add(new Attempt(raw, errors));

            if (errors.isEmpty()) {
                log.info("[스토리보드] {} 검증 통과 (시도 {})", menu.storeName(), i + 1);
                Storyboard withMeta = parsed.withMeta(new Storyboard.Meta(menu.category(), StoryboardPrompt.VERSION));
                return new Result(withMeta, attempts);
            }
            log.warn("[스토리보드] {} 검증 실패 {}건: {}", menu.storeName(), errors.size(), errors);
            request = prompt.buildFix(original, raw, errors);
        }
        throw new StoryboardGenerationException(attempts);
    }

    /** 시도 한 번의 LLM 원문과 검증 오류 (디버깅·프롬프트 개선용) */
    public record Attempt(String rawOutput, List<String> errors) {}

    public record Result(Storyboard storyboard, List<Attempt> attempts) {}

    public static class StoryboardGenerationException extends RuntimeException {
        private final List<Attempt> attempts;

        public StoryboardGenerationException(List<Attempt> attempts) {
            super("장면 JSON 검증을 " + attempts.size() + "번 모두 통과하지 못했어요. 마지막 오류: "
                    + attempts.get(attempts.size() - 1).errors());
            this.attempts = attempts;
        }

        public List<Attempt> getAttempts() {
            return attempts;
        }
    }
}
