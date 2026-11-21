package com.commicom.itda.domain.generation.controller;

import com.commicom.itda.domain.generation.storyboard.MenuData;
import com.commicom.itda.domain.generation.storyboard.MenuExtractionPrompt;
import com.commicom.itda.domain.generation.storyboard.MenuExtractionPrompt.Slot;
import com.commicom.itda.domain.generation.storyboard.MenuExtractionValidator;
import com.commicom.itda.domain.generation.storyboard.MenuExtractor;
import com.commicom.itda.domain.generation.storyboard.MenuExtractor.MenuExtractionException;
import com.commicom.itda.domain.generation.storyboard.StoryboardJson;
import com.commicom.itda.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * local 프로필 전용: 메뉴 추출 프롬프트 다듬기용.
 * 이미지 순서는 앱과 같게 [음식·가게 사진들..., 메뉴판 사진들...] — 이 순서가 photos[].index가 된다.
 */
@Tag(name = "Dev", description = "로컬 테스트 전용")
@Profile("local")
@RestController
@RequestMapping("/api/dev/menu-extraction")
@RequiredArgsConstructor
public class DevMenuExtractionController {

    private final MenuExtractionPrompt prompt;
    private final MenuExtractionValidator validator;
    private final ObjectProvider<MenuExtractor> extractor;

    @Operation(summary = "[로컬] 메뉴 추출 프롬프트",
            description = "claude.ai에 이 글을 붙여 넣고, 이미지를 음식·가게 사진 → 메뉴판 사진 순서로 첨부해서 보낸다.")
    @GetMapping(value = "/prompt", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String prompt(@Parameter(description = "가게 이름") @RequestParam(required = false) String storeName,
                         @Parameter(description = "업종 코드 (예: RESTAURANT, CAFE_BAKERY_PUB)") @RequestParam(required = false) String category,
                         @Parameter(description = "음식·가게 사진 장수") @RequestParam(defaultValue = "0") int photoCount,
                         @Parameter(description = "메뉴판 사진 장수") @RequestParam(defaultValue = "1") int menuBoardCount,
                         @Parameter(description = "추가 정보 (지도에서 가져온 메뉴, 대표 메뉴 등)") @RequestParam(required = false) String extraInfo) {
        return prompt.build(storeName, category, slots(photoCount, menuBoardCount), extraInfo);
    }

    @Operation(summary = "[로컬] 메뉴 추출 응답 검증",
            description = "본문에 모델 응답을 그대로 붙여 넣는다. photoCount는 음식·가게 사진 장수(메뉴판 제외). errors가 비면 통과.")
    @PostMapping(value = "/validate", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ApiResponse<Map<String, Object>> validate(@RequestParam int photoCount, @RequestBody String output) {
        try {
            MenuData data = StoryboardJson.parseMenu(output);
            List<String> errors = validator.validate(data, photoCount);
            return ApiResponse.onSuccess(Map.of("passed", errors.isEmpty(), "errors", errors));
        } catch (IOException e) {
            return ApiResponse.onSuccess(Map.of("passed", false,
                    "errors", List.of("JSON으로 읽을 수 없습니다: " + e.getMessage())));
        }
    }

    @Operation(summary = "[로컬] 메뉴 추출 실행 (Bedrock 호출)",
            description = "추출 → 검증 → 최대 2회 재요청까지 실제로 돌린다. 시도별 원문과 오류를 함께 돌려준다.")
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Object> extract(@RequestParam(required = false) String storeName,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String extraInfo,
                                       @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
                                       @RequestPart(value = "menuBoards", required = false) List<MultipartFile> menuBoards) {
        List<MenuExtractor.Image> images = new ArrayList<>();
        addImages(images, photos, Slot.PHOTO);
        addImages(images, menuBoards, Slot.MENU_BOARD);
        if (images.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지를 한 장 이상 올려 주세요");
        }
        try {
            return ApiResponse.onSuccess(extractor.getObject().extract(storeName, category, extraInfo, images));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        } catch (MenuExtractionException e) {
            return ApiResponse.onSuccess(Map.of("failed", true, "attempts", e.getAttempts()));
        }
    }

    private static List<Slot> slots(int photoCount, int menuBoardCount) {
        if (photoCount < 0 || menuBoardCount < 0
                || photoCount > MenuExtractor.MAX_PHOTOS || menuBoardCount > MenuExtractor.MAX_MENU_BOARDS) {
            throw new ResponseStatusException(BAD_REQUEST, "사진은 " + MenuExtractor.MAX_PHOTOS + "장, 메뉴판은 "
                    + MenuExtractor.MAX_MENU_BOARDS + "장까지예요");
        }
        List<Slot> slots = new ArrayList<>(Collections.nCopies(photoCount, Slot.PHOTO));
        slots.addAll(Collections.nCopies(menuBoardCount, Slot.MENU_BOARD));
        return slots;
    }

    private static void addImages(List<MenuExtractor.Image> out, List<MultipartFile> files, Slot slot) {
        if (files == null) return;
        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            try {
                out.add(new MenuExtractor.Image(f.getBytes(), slot));
            } catch (IOException e) {
                throw new ResponseStatusException(BAD_REQUEST, "이미지를 읽지 못했어요: " + f.getOriginalFilename());
            }
        }
    }
}
