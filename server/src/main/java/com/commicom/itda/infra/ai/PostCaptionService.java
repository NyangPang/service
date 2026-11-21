package com.commicom.itda.infra.ai;

import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 피드 게시물 본문을 씀 (Bedrock Claude).
 * 가게 이름·주소·영업시간·가게 소개·메뉴와 사장님 한마디를 모아 3~5문장 본문으로 만든다.
 * AI 호출이 실패하면 사장님 한마디를 그대로 씀 (게시물 생성은 계속)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostCaptionService {

    /** 본문 최대 글자 수. DB 칸(shortform.caption)은 500자. 피드는 두 줄만 보이고 [더보기]로 펼침 */
    public static final int MAX_LENGTH = 300;

    private static final String PROMPT = """
            당신은 동네 가게의 SNS 게시물 본문을 쓰는 카피라이터입니다.
            아래 가게 정보로 손님 피드에 올릴 게시물 본문을 한국어로 써 주세요.

            [가게 정보]
            가게 이름: %s
            업종: %s
            주소: %s
            영업시간: %s
            가게 소개: %s
            메뉴:
            %s
            사장님 한마디: %s
            참고 (앱에서 받은 메뉴·수정 요청): %s

            [쓰는 법]
            - 3~5문장, %d자 이내. 친구에게 가게를 소개하듯 친근하고 자연스러운 말투로 씁니다
            - 이 순서로 씁니다
              1. 가게 이름과 사장님 한마디로 시작합니다. 사장님이 말한 장점은 단어 그대로 살립니다 ("고기"를 "음식"으로 바꾸지 않기)
              2. 가게 소개에서 분위기나 특징을 한 문장으로 전합니다
              3. 대표 메뉴부터 1~3개를 가격과 함께 소개합니다 (가격은 "4,500원" 형식)
              4. 마지막 줄에 주소를 따로 적습니다. 영업시간이 있으면 그 뒤에 붙입니다 (예: "📍 서울 노원구 광운로 20 · 매일 10:00~21:00")
            - 위 [가게 정보]에 있는 것만 씁니다. 가격·할인·수상·웨이팅·원산지처럼 없는 사실은 지어내지 마세요
            - 값이 "없음"인 항목은 건너뜁니다. 가격이 없는 메뉴는 이름만 씁니다
            - 수정 요청이 있으면 반영합니다
            - 이모지는 주소 앞 📍를 포함해 3개까지
            - 해시태그·따옴표·제목·설명 없이 본문만 출력합니다
            """;

    private final ChatClient chatClient;

    /**
     * @param appeal   사장님 한마디 (nullable)
     * @param menuInfo 앱에서 받은 메뉴·수정 요청 한 덩어리 (nullable)
     * @param menus    사장님이 메뉴 확인 화면에서 확정한 메뉴 (nullable). 있으면 이걸 메뉴로 쓴다
     */
    public String write(Store store, String appeal, String menuInfo, List<MenuItemDto> menus) {
        String fallback = isBlank(appeal) ? null : trim(appeal);
        try {
            String prompt = prompt(store, appeal, menuInfo, menus);
            String caption = chatClient.prompt().user(prompt).call().content();
            caption = caption == null ? "" : caption.strip().replaceAll("^[\"'“”]+|[\"'“”]+$", "");
            log.info("[게시물 본문] {} → {}", isBlank(appeal) ? "(한마디 없음)" : appeal.trim(), caption.replace('\n', ' '));
            return caption.isBlank() ? fallback : trim(caption);
        } catch (Exception e) {
            log.warn("[게시물 본문] AI 실패, 사장님 한마디 그대로 사용: {}", e.getMessage());
            return fallback;
        }
    }

    static String prompt(Store store, String appeal, String menuInfo, List<MenuItemDto> menus) {
        return PROMPT.formatted(
                store.getName(),
                store.getCategory().getDescription(),
                orNone(store.getAddress()),
                orNone(store.getBusinessHours()),
                orNone(store.getDescription()),
                menuLines(menus),
                orNone(appeal),
                orNone(menuInfo),
                MAX_LENGTH);
    }

    /** 확정 메뉴 → "- 시그니처 라떼 5,500원 (대표 메뉴) — 설명" 줄들. 없으면 "없음" */
    static String menuLines(List<MenuItemDto> menus) {
        if (menus == null || menus.isEmpty()) return "없음";
        StringBuilder sb = new StringBuilder();
        for (MenuItemDto m : menus) {
            if (m == null || isBlank(m.name())) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append("- ").append(m.name().strip());
            if (m.price() != null) sb.append(' ').append(String.format("%,d원", m.price()));
            if (Boolean.TRUE.equals(m.signature())) sb.append(" (대표 메뉴)");
            if (!isBlank(m.description())) sb.append(" — ").append(m.description().strip());
        }
        return sb.isEmpty() ? "없음" : sb.toString();
    }

    /** MAX_LENGTH를 넘으면 그 안의 마지막 줄바꿈이나 문장 끝에서 자른다 (글자 중간에서 끊기지 않게) */
    static String trim(String text) {
        String t = text.strip();
        if (t.length() <= MAX_LENGTH) return t;
        String head = t.substring(0, MAX_LENGTH);
        int cut = Math.max(head.lastIndexOf('\n'),
                Math.max(head.lastIndexOf(". "), Math.max(head.lastIndexOf("! "), head.lastIndexOf("? "))));
        return (cut > MAX_LENGTH / 2 ? head.substring(0, cut + 1) : head).strip();
    }

    private static String orNone(String s) {
        return isBlank(s) ? "없음" : s.strip();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
