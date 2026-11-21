package com.commicom.itda.domain.shortform.dto;

import com.commicom.itda.domain.shortform.entity.Shortform;

import java.time.LocalDateTime;
import java.util.List;

public record ShortformSummaryResponse(
        Long shortformId,
        Long storeId,
        String storeName,
        String imageUrl,
        /** 옆으로 넘겨 볼 사진 전체 (첫 장 = imageUrl, 최대 5장) */
        List<String> imageUrls,
        /** 홍보 영상 (영상 게시물만, 없으면 null). 있으면 imageUrl 은 포스터 */
        String videoUrl,
        String title,
        /** 게시물 소개 글 (사장님 어필을 다듬은 문장, 없으면 null) */
        String caption,
        LocalDateTime createdAt,
        /** PRO 가게 게시물 (피드 우선 노출·추천 배지) */
        boolean promoted,
        /** 처음 게시물에서 고쳐 만든 횟수 (사장님 내 게시물: 무료 수정 남은 횟수 표시) */
        int revisionCount
) {

    public static ShortformSummaryResponse from(Shortform shortform, boolean promoted) {
        return new ShortformSummaryResponse(
                shortform.getId(),
                shortform.getStore().getId(),
                shortform.getStore().getName(),
                shortform.getImageUrl(),
                shortform.getImageUrls(),
                shortform.getVideoUrl(),
                shortform.getTitle(),
                shortform.getCaption(),
                shortform.getCreatedAt(),
                promoted,
                shortform.getRevisionCount()
        );
    }
}
