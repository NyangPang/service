package com.commicom.itda.domain.generation.service;

import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.generation.entity.Generation;
import com.commicom.itda.domain.generation.repository.GenerationRepository;
import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.repository.StoreRepository;
import com.commicom.itda.global.config.AsyncConfig;
import com.commicom.itda.infra.ai.PostCaptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 게시물 생성 파이프라인 (비동기). PENDING → PROCESSING → COMPLETED / FAILED
 *
 * <pre>
 *   사진·메뉴판 → 메뉴 추출 → (사진이 모자라면) 메뉴 이미지 생성 → 장면 JSON → mp4 → videoUrl 게시물
 *   {@link GenerationVideoService}
 * </pre>
 * 영상을 못 만들면 FAILED. (예전의 Bedrock AI 이미지 1장 게시물 대체 흐름은 모델 종료로 뺐다)
 *
 * 영상은 몇 분 걸리므로 DB 트랜잭션을 그동안 잡고 있지 않고, 상태를 바꿀 때만 짧게 연다.
 */
@Slf4j
@Service
public class GenerationPipelineService {

    private static final String CANCELED_MESSAGE = "사장님이 생성을 취소했어요";
    private static final String FAILED_MESSAGE = "영상을 만들지 못했어요. 잠시 후 다시 시도해 주세요";

    private final GenerationRepository generationRepository;
    private final ShortformRepository shortformRepository;
    private final StoreRepository storeRepository;
    private final PostCaptionService postCaptionService;
    private final GenerationCancelRegistry cancelRegistry;
    private final GenerationVideoService videoService;
    private final TransactionTemplate tx;

    public GenerationPipelineService(GenerationRepository generationRepository,
                                     ShortformRepository shortformRepository,
                                     StoreRepository storeRepository,
                                     PostCaptionService postCaptionService,
                                     GenerationCancelRegistry cancelRegistry,
                                     GenerationVideoService videoService,
                                     PlatformTransactionManager transactionManager) {
        this.generationRepository = generationRepository;
        this.shortformRepository = shortformRepository;
        this.storeRepository = storeRepository;
        this.postCaptionService = postCaptionService;
        this.cancelRegistry = cancelRegistry;
        this.videoService = videoService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Async(AsyncConfig.VIDEO_GENERATION_EXECUTOR)
    public void execute(Long generationId, String menuInfo, String appeal, List<MenuItemDto> menus,
                        List<String> photoUrls, List<String> menuBoardUrls) {
        Store store = tx.execute(status -> start(generationId));
        if (store == null) return;
        log.info("[생성 {}] 시작 - {}", generationId, store.getName());

        try {
            // 게시물 본문: 가게 정보·메뉴·사장님 한마디로 씀 (실패하면 한마디 그대로)
            String caption = postCaptionService.write(store, appeal, menuInfo, menus);

            Shortform shortform = videoService.make(store, menuInfo, appeal, menus, photoUrls, menuBoardUrls, caption,
                    () -> cancelRegistry.isCanceled(generationId));

            Long shortformId = shortform.getId();
            finish(generationId, g -> {
                Shortform saved = shortformRepository.getReferenceById(shortformId);
                // 수정 요청이면 고칠 게시물 + 1 (영상·이미지 게시물 모두 여기서 한 번에)
                saved.markRevision(g.getRevisionCount());
                g.complete(saved);
            });
            log.info("[생성 {}] 완료 → shortformId={} ({})", generationId, shortformId,
                    shortform.getVideoUrl() != null ? "영상" : "이미지");

        } catch (GenerationVideoService.CanceledException e) {
            log.info("[생성 {}] 사장님이 취소함", generationId);
            finish(generationId, g -> g.fail(CANCELED_MESSAGE));
        } catch (Exception e) {
            // 오류 원문은 로그로만 (LLM 검증·FFmpeg 메시지는 사장님이 읽을 글이 아님)
            log.error("[생성 {}] 실패: {}", generationId, e.getMessage(), e);
            finish(generationId, g -> g.fail(FAILED_MESSAGE));
        } finally {
            cancelRegistry.clear(generationId);
        }
    }

    /** PROCESSING 으로 바꾸고 가게를 돌려줌. 없거나 이미 취소했으면 null */
    private Store start(Long generationId) {
        Generation generation = generationRepository.findById(generationId).orElse(null);
        if (generation == null) {
            log.error("Generation {} 을 찾을 수 없음", generationId);
            return null;
        }
        if (cancelRegistry.isCanceled(generationId)) {
            generation.fail(CANCELED_MESSAGE);
            cancelRegistry.clear(generationId);
            return null;
        }
        generation.startProcessing();
        // 트랜잭션 밖에서도 이름·업종을 읽을 수 있게 프록시가 아닌 실제 엔티티로
        return storeRepository.findById(generation.getStore().getId()).orElseThrow();
    }

    private void finish(Long generationId, java.util.function.Consumer<Generation> change) {
        tx.executeWithoutResult(status -> generationRepository.findById(generationId).ifPresent(change));
    }
}
