package com.commicom.itda.domain.metrics.controller;

import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse;
import com.commicom.itda.domain.metrics.service.StoreMetricsService;
import com.commicom.itda.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Metrics", description = "사장님 성과지표")
@RestController
@RequestMapping("/api/stores/{storeId}/metrics")
@RequiredArgsConstructor
public class StoreMetricsController {

    private final StoreMetricsService metricsService;

    @Operation(summary = "성과지표 조회 (사장님)",
            description = "게시물 조회수·스크랩 수·QR 방문 인증 수(이전 기간 대비), 조회 후 방문율, 날짜별 방문 인증, 게시물별 조회·스크랩. "
                    + "period: 7d(최근 7일, 기본) / 30d(최근 30일) / all(전체 기간, 비교 없음). 내 가게만 (STORE403_2)")
    @GetMapping
    public ApiResponse<StoreMetricsResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "7d") String period) {
        return ApiResponse.onSuccess(metricsService.get(memberId, storeId, period));
    }
}
