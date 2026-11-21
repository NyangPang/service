package com.commicom.itda.domain.shortform.dto;

import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.store.entity.Store;

import java.time.LocalDateTime;
import java.util.List;

public record ShortformDetailResponse(
        Long shortformId,
        Long storeId,
        String storeName,
        String storeCategory,
        String storeCategoryName,
        String imageUrl,
        /** 옆으로 넘겨 볼 사진 전체 (첫 장 = imageUrl, 최대 5장) */
        List<String> imageUrls,
        /** 홍보 영상 (영상 게시물만, 없으면 null). 있으면 imageUrl 은 포스터 */
        String videoUrl,
        String title,
        /** 게시물 소개 글 (사장님 어필을 다듬은 문장, 없으면 null) */
        String caption,
        LocalDateTime createdAt,
        /** 처음 게시물에서 고쳐 만든 횟수 (0 = 처음). 무료는 freeRevisions 번까지 고칠 수 있음, PRO는 무제한 */
        int revisionCount,
        int freeRevisions
) {

    public static ShortformDetailResponse from(Shortform shortform) {
        Store store = shortform.getStore();
        return new ShortformDetailResponse(
                shortform.getId(),
                store.getId(),
                store.getName(),
                store.getCategory().name(),
                store.getCategory().getDescription(),
                shortform.getImageUrl(),
                shortform.getImageUrls(),
                shortform.getVideoUrl(),
                shortform.getTitle(),
                shortform.getCaption(),
                shortform.getCreatedAt(),
                shortform.getRevisionCount(),
                Shortform.FREE_REVISIONS
        );
    }
}
