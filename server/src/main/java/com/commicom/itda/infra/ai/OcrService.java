package com.commicom.itda.infra.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Lazy
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ChatClient chatClient;

    private static final String OCR_PROMPT =
            "이 메뉴판 이미지에서 메뉴 항목과 가격을 추출해 주세요. " +
            "반드시 아래 형식으로만 출력하세요. 설명이나 다른 텍스트는 절대 포함하지 마세요.\n\n" +
            "[메뉴명, 가격]\n" +
            "[메뉴명, 가격]\n\n" +
            "가격은 원 단위 숫자만 적으세요. 메뉴판이 천 원 단위(예: 4.5)로 적혀 있으면 4500처럼 바꿔 적으세요.\n" +
            "가격을 알 수 없으면 '가격 미표기'로 적어주세요.";

    public String extractMenuText(MultipartFile imageFile) {
        try {
            // 크기를 줄이고 실제 형식에 맞는 MIME 타입을 붙임 (예전엔 PNG도 image/jpeg로 보내서 Anthropic API가 거절함)
            Media media = AiImage.toMedia(imageFile.getBytes());
            log.info("OCR 요청: {} {}", media.getMimeType(), imageFile.getOriginalFilename());
            return chatClient.prompt()
                    .user(u -> u.text(OCR_PROMPT).media(media))
                    .call()
                    .content();
        } catch (IOException e) {
            throw new RuntimeException("이미지 읽기 실패: " + e.getMessage(), e);
        }
    }
}
