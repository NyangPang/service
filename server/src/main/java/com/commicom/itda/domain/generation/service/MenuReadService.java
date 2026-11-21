package com.commicom.itda.domain.generation.service;

import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.generation.storyboard.MenuData;
import com.commicom.itda.domain.generation.storyboard.MenuExtractionPrompt.Slot;
import com.commicom.itda.domain.generation.storyboard.MenuExtractor;
import com.commicom.itda.domain.generation.storyboard.MenuExtractor.MenuExtractionException;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.repository.StoreRepository;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴 확인 화면용 메뉴 읽기: 메뉴판 사진 → {@link MenuExtractor} → 메뉴 목록.
 * 영상 만들 때와 같은 추출기라 화면에서 본 메뉴가 그대로 영상에 들어간다.
 * LLM 호출이 길어(재시도 포함 수십 초) DB 트랜잭션은 잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuReadService {

    private final StoreRepository storeRepository;
    private final ObjectProvider<MenuExtractor> menuExtractor;

    public List<MenuItemDto> read(Long memberId, Long storeId, List<MultipartFile> menuBoards) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!store.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.STORE_FORBIDDEN);
        }

        List<MenuExtractor.Image> images = new ArrayList<>();
        for (MultipartFile f : menuBoards == null ? List.<MultipartFile>of() : menuBoards) {
            if (f == null || f.isEmpty()) continue;
            try {
                images.add(new MenuExtractor.Image(f.getBytes(), Slot.MENU_BOARD));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        if (images.isEmpty() || images.size() > MenuExtractor.MAX_MENU_BOARDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            MenuData menu = menuExtractor.getObject()
                    .extract(store.getName(), store.getCategory().name(), null, images).menuData();
            return menu.menus().stream().map(MenuItemDto::from).toList();
        } catch (MenuExtractionException e) {
            log.warn("[메뉴 읽기] {} 실패: {}", store.getName(), e.getMessage());
            throw new BusinessException(ErrorCode.MENU_READ_FAILED);
        }
    }
}
