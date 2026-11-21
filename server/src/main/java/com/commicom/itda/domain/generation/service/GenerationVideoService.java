package com.commicom.itda.domain.generation.service;

import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.generation.storyboard.MenuData;
import com.commicom.itda.domain.generation.storyboard.MenuExtractionPrompt.Slot;
import com.commicom.itda.domain.generation.storyboard.MenuExtractor;
import com.commicom.itda.domain.generation.storyboard.StoryboardVideoPipeline;
import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.service.ShortformVideoService;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.infra.storage.StorageService;
import com.commicom.itda.infra.video.storyboard.MenuImageGenerator;
import com.commicom.itda.infra.video.storyboard.StoryboardRenderer;
import com.commicom.itda.infra.video.storyboard.StoryboardVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * 게시물 생성의 영상 단계: 사장님 사진·메뉴판 → 메뉴 추출 → (사진이 없으면) 메뉴 이미지 생성 → 장면 JSON → mp4 → 비공개 게시물.
 * 단계마다 쓰는 서비스는 원래 있던 것 그대로이고, 여기서는 S3 사진을 받아 순서대로 이어 주기만 한다.
 */
@Slf4j
@Service
public class GenerationVideoService {

    /** 게시물 사진은 4장까지 (GenerationRequest.photoUrls 와 같게) */
    private static final int MAX_POST_PHOTOS = 4;

    private final ObjectProvider<MenuExtractor> menuExtractor;
    private final ObjectProvider<StoryboardVideoPipeline> videoPipeline;
    private final ShortformVideoService shortformVideoService;
    private final StorageService storageService;
    private final boolean useRunway;

    public GenerationVideoService(ObjectProvider<MenuExtractor> menuExtractor,
                                  ObjectProvider<StoryboardVideoPipeline> videoPipeline,
                                  ShortformVideoService shortformVideoService,
                                  StorageService storageService,
                                  @Value("${generation.video.runway:true}") boolean useRunway) {
        this.menuExtractor = menuExtractor;
        this.videoPipeline = videoPipeline;
        this.shortformVideoService = shortformVideoService;
        this.storageService = storageService;
        this.useRunway = useRunway;
    }

    /** 사장님이 중간에 취소함 — 영상 단계를 멈추고 이미지 게시물로 대체하지도 않는다 */
    public static class CanceledException extends RuntimeException {
        public CanceledException() {
            super("canceled");
        }
    }

    /**
     * @param confirmedMenus 사장님이 메뉴 확인 화면에서 확정한 메뉴. 있으면 메뉴판을 다시 읽지 않고 이 메뉴만 쓴다
     * @param photoUrls     게시물 사진 (메뉴판이 섞여 있을 수 있음, 게시물엔 그대로 붙음)
     * @param menuBoardUrls 메뉴판 사진. 확정 메뉴가 없을 때만 메뉴 읽기에 쓰고, 영상 장면에서는 뺀다
     * @param caption       게시물 소개 글 (이미 다듬은 것)
     * @param canceled      긴 단계 사이마다 확인. true면 {@link CanceledException}
     */
    public Shortform make(Store store, String menuInfo, String appeal, List<MenuItemDto> confirmedMenus,
                          List<String> photoUrls, List<String> menuBoardUrls,
                          String caption, BooleanSupplier canceled) throws Exception {
        List<MenuData.Menu> fixedMenus = toMenus(confirmedMenus);
        List<String> boards = menuBoardUrls == null ? List.of() : menuBoardUrls.stream()
                .limit(MenuExtractor.MAX_MENU_BOARDS).toList();
        // 확정 메뉴가 있으면 메뉴판은 받지도 읽지도 않는다 (사장님이 지운 메뉴가 되살아나거나 고친 가격이 덮이지 않게)
        List<String> boardsToRead = fixedMenus.isEmpty() ? boards : List.of();
        List<String> scenePhotoUrls = photoUrls == null ? List.of() : photoUrls.stream()
                .filter(u -> !boards.contains(u)).limit(MenuExtractor.MAX_PHOTOS).toList();
        List<String> postPhotoUrls = new ArrayList<>(photoUrls == null ? List.of() : photoUrls);

        Path dir = Files.createTempDirectory("generation-video-");
        try {
            // ① S3에서 사진·메뉴판 받기 (사진 순서 = 메뉴 데이터 photos[].index = 장면 image_index)
            List<Path> photos = download(scenePhotoUrls, dir, "photo");
            List<Path> boardFiles = download(boardsToRead, dir, "board");

            // ② 메뉴 추출: 확정 메뉴가 있으면 그 메뉴로 사진만 분류, 없으면 메뉴판에서 읽기 (menuInfo는 추가 정보)
            List<MenuExtractor.Image> images = new ArrayList<>();
            for (Path p : photos) images.add(new MenuExtractor.Image(Files.readAllBytes(p), Slot.PHOTO));
            for (Path p : boardFiles) images.add(new MenuExtractor.Image(Files.readAllBytes(p), Slot.MENU_BOARD));
            MenuData menu = menuExtractor.getObject()
                    .extract(store.getName(), store.getCategory().name(), extraInfo(menuInfo, fixedMenus), images, fixedMenus)
                    .menuData();
            log.info("[영상 생성] {} 메뉴 {}개 ({})", store.getName(), menu.menus().size(),
                    fixedMenus.isEmpty() ? "메뉴판에서 읽음" : "사장님 확정 메뉴");
            check(canceled);

            // ③ 사진이 3장보다 적으면 모자란 만큼 메뉴 이미지 생성 (Runway) — 영상 파이프라인의 addGeneratedImages를 그대로 씀.
            //    여기서 먼저 부르는 건 실패해도 있는 사진으로 계속하고, 만든 이미지를 게시물 사진에도 올리기 위해서
            if (menu.imageRequests() != null && !menu.imageRequests().isEmpty()) {
                if (useRunway) {
                    try {
                        StoryboardVideoPipeline.Images generated = videoPipeline.getObject().addGeneratedImages(menu, photos);
                        menu = generated.menu();
                        photos = generated.photos();
                        log.info("[영상 생성] {} 메뉴 이미지 {}장 (새로 {} credits)", store.getName(),
                                generated.generated().size(), generated.creditsSpent());
                        List<Path> newImages = generated.generated().stream().map(MenuImageGenerator.Generated::image).toList();
                        int room = Math.max(0, MAX_POST_PHOTOS - postPhotoUrls.size());
                        postPhotoUrls.addAll(shortformVideoService.uploadGeneratedImages(store,
                                newImages.subList(0, Math.min(room, newImages.size()))));
                    } catch (Exception e) {
                        log.warn("[영상 생성] {} 메뉴 이미지 생성 실패, 있는 사진으로만 진행: {}", store.getName(), e.getMessage());
                        menu = withoutImageRequests(menu);
                    }
                } else {
                    log.info("[영상 생성] {} Runway 끔 → 메뉴 이미지 생성 건너뜀", store.getName());
                    menu = withoutImageRequests(menu);
                }
                check(canceled);
            }

            // ④ 장면 JSON → mp4 (사진이 하나도 없으면 여기서 IllegalArgumentException → 호출한 쪽이 이미지 게시물로 대체)
            StoryboardVideoPipeline.Result result = videoPipeline.getObject().make(menu, appeal, photos, useRunway, store.getAddress());
            StoryboardVideoService.Result video = result.video();
            // 크레딧이 실제로 나갔는지 로그만 보고 알 수 있게: 새 클립 = 크레딧 사용, 캐시 = 같은 사진·프롬프트 재사용, 대체 = 줌 효과
            List<StoryboardRenderer.SceneReport> ai = video.scenes().stream().filter(s -> "ai".equals(s.motion())).toList();
            log.info("[영상 생성] {} 영상 {}초 · ai 장면 {}개 → Runway 새 클립 {} · 캐시 {} · 줌 대체 {} (Runway {})",
                    store.getName(), String.format("%.1f", video.durationSec()), ai.size(),
                    ai.stream().filter(s -> s.clip() == StoryboardRenderer.State.NEW).count(),
                    ai.stream().filter(s -> s.clip() == StoryboardRenderer.State.CACHED).count(),
                    ai.stream().filter(s -> s.clip() == StoryboardRenderer.State.FALLBACK).count(),
                    useRunway ? "켬" : "끔");
            check(canceled);

            // ⑤ S3 업로드 + 비공개 게시물
            return shortformVideoService.saveDraft(store, video.video(), video.thumbnail(),
                    result.storyboard().title(), caption, postPhotoUrls);
        } finally {
            deleteQuietly(dir);
        }
    }

    private List<Path> download(List<String> urls, Path dir, String prefix) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String url : urls) {
            String name = url.substring(url.lastIndexOf('/') + 1);
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "") : ".jpg";
            Path dest = dir.resolve(String.format("%s-%02d%s", prefix, files.size(), ext));
            storageService.download(url, dest);
            files.add(dest);
        }
        return files;
    }

    /**
     * 확정 메뉴 → 메뉴 데이터 형식. 이름 앞뒤 공백·중복은 빼고, 가격으로 보기 어려운 값(100원 미만, 100만 원 초과, 10원 단위가 아님)은
     * 모르는 가격(null)으로 둔다 — 영상 문구에 틀린 가격이 나오는 것보다 가격을 빼는 게 낫다
     */
    static List<MenuData.Menu> toMenus(List<MenuItemDto> items) {
        if (items == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<MenuData.Menu> menus = new ArrayList<>();
        for (MenuItemDto item : items) {
            String name = item == null || item.name() == null ? "" : item.name().trim();
            if (name.isEmpty() || !seen.add(name)) continue;
            Integer price = item.price();
            if (price != null && (price < 100 || price > 1_000_000 || price % 10 != 0)) price = null;
            String description = item.description() == null || item.description().isBlank() ? null : item.description().trim();
            menus.add(new MenuData.Menu(name, price == null ? null : String.format("%,d", price), price, description,
                    Boolean.TRUE.equals(item.signature())));
        }
        return menus;
    }

    /** 추출 프롬프트 [추가 정보]: 확정 메뉴를 맨 앞에 적어 모델이 사진 menu_ref를 이 이름 그대로 쓰게 */
    static String extraInfo(String menuInfo, List<MenuData.Menu> fixedMenus) {
        if (fixedMenus.isEmpty()) return menuInfo;
        StringBuilder sb = new StringBuilder("사장님이 확인한 메뉴 (menus는 이 목록 그대로, 다른 메뉴를 더하지 마세요."
                + " photos[].menu_ref와 image_requests[].menu_ref도 아래 이름을 글자 그대로 씁니다):");
        for (MenuData.Menu m : fixedMenus) {
            sb.append("\n- ").append(m.name());
            if (m.price() != null) sb.append(" (").append(m.priceText()).append("원)");
            if (m.signature()) sb.append(" [대표 메뉴]");
            if (m.description() != null) sb.append(" — ").append(m.description());
        }
        if (menuInfo != null && !menuInfo.isBlank()) sb.append("\n").append(menuInfo.trim());
        return sb.toString();
    }

    private static MenuData withoutImageRequests(MenuData m) {
        return new MenuData(m.storeName(), m.category(), m.menus(), m.reviewKeywords(), m.photos(), List.of());
    }

    private static void check(BooleanSupplier canceled) {
        if (canceled.getAsBoolean()) throw new CanceledException();
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
