package com.commicom.itda.domain.generation.dto;

import com.commicom.itda.domain.generation.storyboard.Emphasis;
import com.commicom.itda.domain.generation.storyboard.VideoStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GenerationRequest(
        @NotNull(message = "가게 ID를 입력해 주세요") Long storeId,
        /** 메뉴판 OCR 결과 등 메뉴 정보 (선택) */
        String menuInfo,
        /** 사장님이 적은 가게 어필 → AI가 다듬어 게시물 소개 글(caption)로 (앱에선 필수, 비우면 소개 글 없음) */
        @Size(max = 200, message = "가게 어필은 200자 이내로 적어 주세요") String appeal,
        /** 게시물에 같이 넣을 사장님 사진 URL (POST /api/stores/{storeId}/photos 로 올린 것, 최대 4장, 선택) */
        @Size(max = 4, message = "사진은 4장까지 넣을 수 있어요") List<String> photoUrls,
        /**
         * 메뉴판 사진 URL (POST /api/stores/{storeId}/photos 로 올린 것, 최대 3장, 선택).
         * 영상 만들 때 메뉴 읽기에만 쓰고 영상 장면에는 넣지 않는다. photoUrls 에 같은 주소가 있어도 된다 (게시물 사진으로는 그대로 붙음)
         */
        @Size(max = 3, message = "메뉴판 사진은 3장까지 넣을 수 있어요") List<String> menuBoardUrls,
        /**
         * 사장님이 메뉴 확인 화면에서 확정한 메뉴 (POST /api/generation/menus 결과를 고친 것, 선택).
         * 있으면 영상은 메뉴판을 다시 읽지 않고 이 메뉴만 쓴다. 없으면 메뉴판(menuBoardUrls)에서 읽는다
         */
        @Valid @Size(max = 30, message = "메뉴는 30개까지 넣을 수 있어요") List<MenuItemDto> menus,
        /** 고쳐 만들 게시물 ID (수정 요청일 때만). 무료는 게시물마다 1번까지, PRO는 무제한 */
        Long revisionOf,
        /** 생성 느낌: 영상 스타일 (MENU_FOCUS / PERSON_INTRO / NEWS_PROMO, 선택). 없으면 기본 프롬프트·기본 목소리 */
        VideoStyle videoStyle,
        /** 생성 느낌: 강조 포인트 (FAST_CUTS / MENU_CLOSEUP / BIG_CAPTION / FAST_TEMPO / SLOW_TEMPO 여러 개, 선택) */
        @Size(max = 5, message = "강조 포인트는 5개까지 고를 수 있어요") List<Emphasis> emphases
) {}
