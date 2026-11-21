package com.commicom.itda.domain.generation.service;

import com.commicom.itda.domain.generation.dto.GenerationRequest;
import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.generation.dto.GenerationResponse;
import com.commicom.itda.domain.generation.dto.GenerationStatusResponse;
import com.commicom.itda.domain.generation.entity.Generation;
import com.commicom.itda.domain.generation.entity.GenerationStatus;
import com.commicom.itda.domain.generation.repository.GenerationRepository;
import com.commicom.itda.domain.member.entity.Member;
import com.commicom.itda.domain.member.repository.MemberRepository;
import com.commicom.itda.domain.pro.repository.ProSubscriptionRepository;
import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.repository.StoreRepository;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import com.commicom.itda.global.util.KstTime;
import com.commicom.itda.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerationService {

    private final GenerationRepository generationRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    private final GenerationPipelineService pipelineService;
    private final StorageService storageService;
    private final GenerationCancelRegistry cancelRegistry;
    private final ShortformRepository shortformRepository;
    private final ProSubscriptionRepository proRepository;

    @Transactional
    public GenerationResponse requestGeneration(Long memberId, GenerationRequest request) {
        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        // 내 가게 게시물만 만들 수 있음
        if (!store.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.STORE_FORBIDDEN);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 사장님이 취소한 요청은 아직 끝나지 않았어도 새로 만들 수 있게 빼고 셈
        boolean hasActiveRequest = generationRepository.findAllByStoreAndStatusIn(
                        store, List.of(GenerationStatus.PENDING, GenerationStatus.PROCESSING)).stream()
                .anyMatch(g -> !cancelRegistry.isCanceled(g.getId()));
        if (hasActiveRequest) {
            throw new BusinessException(ErrorCode.GENERATION_CONFLICT);
        }

        int revisionCount = nextRevisionCount(store, request.revisionOf());

        Generation generation = Generation.builder()
                .store(store)
                .requestedBy(member)
                .status(GenerationStatus.PENDING)
                .revisionCount(revisionCount)
                .build();
        generationRepository.save(generation);

        // 트랜잭션 커밋 완료 후에 비동기 파이프라인 실행 (커밋 전엔 DB에서 못 찾음)
        Long generationId = generation.getId();
        String menuInfo = request.menuInfo();
        String appeal = request.appeal();
        List<MenuItemDto> menus = request.menus() == null ? List.of() : request.menus();
        // 게시물에 넣을 사장님 사진: 이 가게 사진 폴더에 올라간 것만 (POST /api/stores/{storeId}/photos)
        List<String> photoUrls = ownUploads(request.photoUrls(), store);
        // 메뉴판 사진: 영상 만들 때 메뉴 읽기에만 씀 (같은 폴더 검사)
        List<String> menuBoardUrls = ownUploads(request.menuBoardUrls(), store);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pipelineService.execute(generationId, menuInfo, appeal, menus, photoUrls, menuBoardUrls);
            }
        });

        return GenerationResponse.from(generation);
    }

    /**
     * 수정 요청이면 고칠 게시물의 수정 횟수 + 1. 무료는 게시물마다 FREE_REVISIONS 번까지, PRO는 무제한.
     * 새로 만들기(revisionOf 없음)는 0.
     */
    int nextRevisionCount(Store store, Long revisionOf) {
        if (revisionOf == null) return 0;
        Shortform base = shortformRepository.findById(revisionOf)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHORTFORM_NOT_FOUND));
        if (!base.getStore().getId().equals(store.getId())) {
            throw new BusinessException(ErrorCode.STORE_FORBIDDEN);
        }
        boolean pro = proRepository.findByStoreId(store.getId())
                .map(p -> p.isActive(KstTime.now()))
                .orElse(false);
        if (!pro && base.getRevisionCount() >= Shortform.FREE_REVISIONS) {
            throw new BusinessException(ErrorCode.GENERATION_REVISION_LIMIT);
        }
        return base.getRevisionCount() + 1;
    }

    /** 이 가게 사진 폴더에 올라간 주소만 (다른 곳 주소를 저장하거나 내려받지 않게) */
    private List<String> ownUploads(List<String> urls, Store store) {
        return urls == null ? List.of()
                : urls.stream().filter(u -> storageService.isUploadedUnder(u, "stores/" + store.getId())).toList();
    }

    @Transactional(readOnly = true)
    public GenerationStatusResponse getStatus(Long memberId, Long generationId) {
        Generation generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_NOT_FOUND));

        if (!generation.getRequestedBy().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.GENERATION_FORBIDDEN);
        }

        return GenerationStatusResponse.from(generation);
    }

    /** 생성 취소 (요청한 사람만). 끝나기 전이면 결과를 저장하지 않고 FAILED(취소)로 끝남 */
    @Transactional(readOnly = true)
    public void cancel(Long memberId, Long generationId) {
        Generation generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getRequestedBy().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.GENERATION_FORBIDDEN);
        }
        if (generation.getStatus() != GenerationStatus.PENDING && generation.getStatus() != GenerationStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.GENERATION_NOT_CANCELABLE);
        }
        cancelRegistry.cancel(generationId);
    }
}
