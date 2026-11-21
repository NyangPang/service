package com.commicom.itda.domain.generation.storyboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 영상 스타일 때문에 메뉴 추출 단계에서 더 만들 이미지 (prompts/menu-extraction-style-*.txt).
 * 메뉴가 잘 보이는 영상은 한 상 차림, 신메뉴 알림 영상은 신메뉴·이벤트 이미지가 사진에 없으면 생성을 요청한다.
 *
 * @param guide 메뉴 추출 프롬프트 [image_requests 작성법] 뒤에 붙는 규칙 (없으면 빈 문자열)
 * @param extra 기본 개수(3 − 쓸 수 있는 사진 수)보다 더 요청해도 되는 최대 수
 */
public record StyleImages(String guide, int extra) {

    public static final StyleImages NONE = new StyleImages("", 0);

    /** @param appeal 사장님 한마디 — 신메뉴·이벤트를 여기서 찾는다 */
    public static StyleImages of(VideoOptions options, String appeal) {
        if (options == null || options.extraImages() <= 0) return NONE;
        String guide = read("prompts/menu-extraction-style-" + options.style().name() + ".txt")
                .replace("{appeal}", appeal == null || appeal.isBlank() ? "(없음)" : appeal.trim());
        return new StyleImages(guide, options.extraImages());
    }

    static String read(String path) {
        try (InputStream in = StyleImages.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("프롬프트 파일이 없어요: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 로딩 실패: " + path, e);
        }
    }
}
