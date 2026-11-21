package com.commicom.itda.domain.shortform.service;

import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.entity.StoreCategory;
import com.commicom.itda.infra.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortformVideoServiceTest {

    private final StorageService storage = mock(StorageService.class);
    private final ShortformRepository repository = mock(ShortformRepository.class);
    private final ShortformVideoService service = new ShortformVideoService(storage, repository);

    @TempDir
    Path dir;

    private Store store() {
        Store store = Store.builder().name("[샘플] 골목 끝 베이커리").category(StoreCategory.CAFE_BAKERY_PUB).build();
        ReflectionTestUtils.setField(store, "id", 7L);
        return store;
    }

    @Test
    void 영상과_썸네일을_올리고_비공개_게시물로_저장한다() throws Exception {
        Path video = Files.writeString(dir.resolve("video.mp4"), "mp4");
        Path thumb = Files.writeString(dir.resolve("video.jpg"), "jpg");
        when(storage.uploadFileAsDownload(any(File.class), anyString(), eq("video/mp4"), anyString()))
                .thenReturn("https://cdn/shortforms/7/a.mp4");
        when(storage.uploadFile(any(File.class), anyString(), eq("image/jpeg")))
                .thenReturn("https://cdn/shortforms/7/a.jpg");

        Shortform s = service.saveDraft(store(), video, thumb, "골목 끝 소금빵 맛집", "버터 향 가득", List.of("https://cdn/stores/7/p.jpg"));

        assertThat(s.getVideoUrl()).isEqualTo("https://cdn/shortforms/7/a.mp4");
        assertThat(s.getImageUrl()).isEqualTo("https://cdn/shortforms/7/a.jpg");
        assertThat(s.getTitle()).isEqualTo("골목 끝 소금빵 맛집");
        assertThat(s.getCaption()).isEqualTo("버터 향 가득");
        assertThat(s.getPhotoUrls()).containsExactly("https://cdn/stores/7/p.jpg");
        assertThat(s.isPublished()).isFalse();
        verify(storage).uploadFileAsDownload(any(File.class), startsWith("shortforms/7/"), eq("video/mp4"), eq("골목 끝 베이커리_잇츠.mp4"));
        verify(repository).save(s);
    }

    @Test
    void 제목이_비면_샘플_앞말을_뺀_가게_이름() {
        assertThat(ShortformVideoService.cleanTitle(null, store())).isEqualTo("골목 끝 베이커리");
        assertThat(ShortformVideoService.cleanTitle(" ", store())).isEqualTo("골목 끝 베이커리");
    }

    @Test
    void 다운로드_이름에서_파일이름에_못_쓰는_글자를_바꾼다() {
        assertThat(ShortformVideoService.downloadName("카페/디저트:1호점")).isEqualTo("카페_디저트_1호점_잇츠.mp4");
        assertThat(ShortformVideoService.downloadName("  ")).isEqualTo("잇츠.mp4");
    }

    @Test
    void 영상_파일이_없으면_올리지_않는다() {
        assertThatThrownBy(() -> service.saveDraft(store(), dir.resolve("none.mp4"), dir.resolve("none.jpg"), "t", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).uploadFile(any(), anyString(), anyString());
    }

    @Test
    void 생성_이미지는_가게_사진_폴더에_올린다() throws Exception {
        Path img = Files.writeString(dir.resolve("img-1.png"), "png");
        when(storage.uploadFile(any(File.class), startsWith("stores/7/generated/"), eq("image/png"))).thenReturn("https://cdn/stores/7/generated/x.png");

        assertThat(service.uploadGeneratedImages(store(), List.of(img))).containsExactly("https://cdn/stores/7/generated/x.png");
    }
}
