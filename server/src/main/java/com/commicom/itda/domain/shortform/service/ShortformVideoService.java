package com.commicom.itda.domain.shortform.service;

import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * B 파트 마지막 단계: 렌더링된 영상 → S3 → 비공개 게시물.
 *
 * <pre>
 * (A) 메뉴 추출 → (B) 이미지 생성 → 사장님 확인 → (A) 장면 JSON → (B) 영상 렌더링 → <b>(B) 여기</b>
 * </pre>
 *
 * 게시물은 published=false 로 생기고, 사장님이 미리보기를 보고 [업로드]해야 손님 피드에 보인다 (기존 흐름 그대로).
 * 영상 파일은 다운로드 이름("가게이름_잇츠.mp4")을 붙여 올리므로, 앱의 [다운로드]는 videoUrl 을 열기만 하면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortformVideoService {

    private final StorageService storageService;
    private final ShortformRepository shortformRepository;

    /**
     * @param video     렌더링된 mp4 (StoryboardVideoService 결과)
     * @param thumbnail 같은 영상의 썸네일 jpg → 게시물 대표 이미지(imageUrl)
     * @param title     게시물 제목 (보통 장면 JSON의 title)
     * @param caption   게시물 소개 글 (없으면 null)
     * @param photoUrls 게시물에 같이 넣을 사장님 사진 URL (영상 뒤에 옆으로 넘겨 봄)
     */
    @Transactional
    public Shortform saveDraft(Store store, Path video, Path thumbnail, String title, String caption,
                               List<String> photoUrls) {
        if (!Files.isRegularFile(video)) throw new IllegalArgumentException("영상 파일이 없어요: " + video);
        if (!Files.isRegularFile(thumbnail)) throw new IllegalArgumentException("썸네일 파일이 없어요: " + thumbnail);

        String base = "shortforms/" + store.getId() + "/" + UUID.randomUUID();
        String videoUrl = storageService.uploadFileAsDownload(video.toFile(), base + ".mp4", "video/mp4",
                downloadName(store.getName()));
        String thumbnailUrl = storageService.uploadFile(thumbnail.toFile(), base + ".jpg", "image/jpeg");

        Shortform shortform = Shortform.builder()
                .store(store)
                .imageUrl(thumbnailUrl)
                .videoUrl(videoUrl)
                .title(cleanTitle(title, store))
                .caption(caption)
                .photoUrls(photoUrls == null ? List.of() : photoUrls)
                // 사장님이 결과를 보고 [업로드]해야 손님 피드에 보임
                .published(false)
                .build();
        shortformRepository.save(shortform);
        log.info("[영상 게시물] {} → shortformId={} ({})", store.getName(), shortform.getId(), videoUrl);
        return shortform;
    }

    /**
     * AI가 만든 메뉴 이미지(이미지 생성 단계 결과)를 가게 사진 폴더에 올린다.
     * 사장님 확인 화면에 보여 주고, 게시물 photoUrls 에도 그대로 쓸 수 있다
     * (stores/{storeId}/ 아래라 생성 요청의 photoUrls 검사도 통과).
     */
    public List<String> uploadGeneratedImages(Store store, List<Path> images) {
        List<String> urls = new ArrayList<>();
        for (Path image : images) {
            String name = image.getFileName().toString();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";
            String type = ext.equalsIgnoreCase(".jpg") || ext.equalsIgnoreCase(".jpeg") ? "image/jpeg" : "image/png";
            urls.add(storageService.uploadFile(image.toFile(),
                    "stores/" + store.getId() + "/generated/" + UUID.randomUUID() + ext, type));
        }
        return urls;
    }

    /** 샘플 가게의 "[샘플] " 앞말은 게시물 제목에서 뺀다 (기존 생성 흐름과 같게). 제목이 비면 가게 이름 */
    static String cleanTitle(String title, Store store) {
        String t = title == null || title.isBlank() ? store.getName() : title;
        return t.replaceFirst("^\\[샘플\\]\\s*", "");
    }

    /** 다운로드 파일 이름: 파일 이름에 못 쓰는 글자는 _ 로 */
    static String downloadName(String storeName) {
        String clean = storeName == null ? "" : storeName.replaceFirst("^\\[샘플\\]\\s*", "")
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return (clean.isEmpty() ? "잇츠" : clean + "_잇츠") + ".mp4";
    }
}
