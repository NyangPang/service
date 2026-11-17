package com.commicom.itda.domain.store.dto;

import com.commicom.itda.domain.store.entity.StoreCategory;

import java.time.LocalDate;
import java.util.List;

/** 가게 검색·동네 랭킹 API 응답 */
public final class StoreRankingDtos {

    private StoreRankingDtos() {
    }

    /** GET /api/stores/ranking */
    public record StoreRankingResponse(
            /** 지역 필터 (null 이면 전체 지역) */
            String district,
            /** 업종 필터 (null 이면 모든 업종) */
            StoreCategory category,
            /** 검색어 (null 이면 검색 안 함) */
            String q,
            /** 집계 기간 (7·14·31일) */
            int days,
            /** 집계 시작일·종료일 (KST, 오늘 포함) */
            LocalDate from,
            LocalDate to,
            List<RankedStore> stores
    ) {
    }

    public record RankedStore(
            /** 순위 (방문 수가 같으면 같은 순위) */
            int rank,
            Long storeId,
            String name,
            StoreCategory category,
            String categoryName,
            String address,
            String district,
            String thumbnailUrl,
            /** 기간 내 QR 방문 인증 수 */
            long visitCount
    ) {
    }

    /** GET /api/stores/districts 의 원소 */
    public record DistrictResponse(String name, long storeCount) {
    }
}
