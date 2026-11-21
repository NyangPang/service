package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.infra.video.storyboard.MenuImageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryboardVideoPipelineTest {

    private static final Path SAMPLES = Path.of("docs/storyboard-samples");

    /** A 파트가 넘기는 JSON을 B 파트 레코드로 읽어도 렌더링에 쓰는 값이 하나도 빠지지 않는지 */
    @ParameterizedTest
    @ValueSource(strings = {"01-donkatsu", "02-cafe", "03-izakaya"})
    void A파트_장면JSON을_B파트가_그대로_읽는다(String sample) throws Exception {
        Storyboard a = StoryboardJson.parseStoryboard(Files.readString(SAMPLES.resolve(sample + ".storyboard.json")))
                .withMeta(new Storyboard.Meta("RESTAURANT", "v-test"));

        var b = new ObjectMapper().readValue(StoryboardJson.toJson(a),
                com.commicom.itda.infra.video.storyboard.Storyboard.class);

        assertThat(b.title()).isEqualTo(a.title());
        assertThat(b.totalDuration()).isEqualTo(a.totalDuration());
        assertThat(b.style().mainColor()).isEqualTo(a.style().mainColor());
        assertThat(b.style().mood()).isEqualTo(a.style().mood());
        assertThat(b.bgm().mood()).isEqualTo(a.bgm().mood());
        assertThat(b.scenes()).hasSameSizeAs(a.scenes());
        for (int i = 0; i < a.scenes().size(); i++) {
            Storyboard.Scene sa = a.scenes().get(i);
            var sb = b.scenes().get(i);
            assertThat(sb.id()).isEqualTo(sa.id());
            assertThat(sb.order()).isEqualTo(sa.order());
            assertThat(sb.duration()).isEqualTo(sa.duration());
            assertThat(sb.imageIndex()).isEqualTo(sa.imageIndex());
            assertThat(sb.motion()).isEqualTo(sa.motion());
            assertThat(sb.videoPrompt()).isEqualTo(sa.videoPrompt());
            assertThat(sb.narration()).isEqualTo(sa.narration());
            assertThat(sb.caption().text()).isEqualTo(sa.caption().text());
            assertThat(sb.caption().position()).isEqualTo(sa.caption().position());
            assertThat(sb.caption().emphasis()).isEqualTo(sa.caption().emphasis());
        }
    }

    @Test
    void 사진_장수가_photos와_다르면_LLM_부르기_전에_막는다() throws Exception {
        MenuData cafe = StoryboardJson.parseMenu(Files.readString(SAMPLES.resolve("02-cafe.menu.json")));

        assertThatThrownBy(() -> StoryboardVideoPipeline.checkPhotos(cafe, List.of(Path.of("a.jpg"), Path.of("b.jpg"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2장")
                .hasMessageContaining("3장");

        StoryboardVideoPipeline.checkPhotos(cafe, List.of(Path.of("a.jpg"), Path.of("b.jpg"), Path.of("c.jpg")));
    }

    @Test
    void 이미지_요청이_없으면_메뉴와_사진을_그대로_쓴다(@TempDir Path work) throws Exception {
        MenuData cafe = StoryboardJson.parseMenu(Files.readString(SAMPLES.resolve("02-cafe.menu.json")));
        List<Path> photos = List.of(Path.of("a.jpg"), Path.of("b.jpg"), Path.of("c.jpg"));

        var images = pipeline("", work).addGeneratedImages(cafe, photos);

        assertThat(images.menu()).isSameAs(cafe);
        assertThat(images.photos()).isEqualTo(photos);
        assertThat(images.generated()).isEmpty();
    }

    @Test
    void 이미지_요청이_있는데_Runway_키가_없으면_알려준다(@TempDir Path work) throws Exception {
        MenuData dessert = StoryboardJson.parseMenu(Files.readString(SAMPLES.resolve("04-dessert.menu.json")));

        assertThatThrownBy(() -> pipeline("", work).addGeneratedImages(dessert, List.of(Path.of("store.jpg"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNWAYML_API_SECRET");
    }

    /** 캐시에 이미 있는 이미지로 확인 — Runway를 부르지 않으므로 크레딧이 들지 않는다 */
    @Test
    void 생성_이미지를_photos_끝에_붙이고_사진_목록도_같은_순서로_늘린다(@TempDir Path work) throws Exception {
        MenuData dessert = StoryboardJson.parseMenu(Files.readString(SAMPLES.resolve("04-dessert.menu.json")));
        Path cache = Files.createDirectories(work.resolve("image-cache"));
        for (MenuData.ImageRequest req : dessert.imageRequests()) {
            Files.writeString(cache.resolve("img-" + hash("gen4_image", "720:1280", req.prompt()) + ".png"), "png");
        }
        Path store = Path.of("store.jpg");

        var images = pipeline("test-key", work).addGeneratedImages(dessert, List.of(store));

        assertThat(images.photos()).hasSize(3).first().isEqualTo(store);
        assertThat(images.menu().photos()).extracting(MenuData.Photo::index).containsExactly(0, 1, 2);
        assertThat(images.menu().photos()).extracting(MenuData.Photo::source).containsExactly("upload", "generated", "generated");
        assertThat(images.menu().photos()).extracting(MenuData.Photo::menuRef).containsExactly(null, "크로플", "딸기라떼");
        assertThat(images.menu().imageRequests()).isEmpty();
        assertThat(images.creditsSpent()).isZero();
        StoryboardVideoPipeline.checkPhotos(images.menu(), images.photos());
    }

    private static StoryboardVideoPipeline pipeline(String runwayKey, Path work) {
        return new StoryboardVideoPipeline(null, null, new MenuImageService(runwayKey, work.toString(), false));
    }

    /** MenuImageGenerator 캐시 파일 이름과 같은 규칙 */
    private static String hash(String... parts) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String p : parts) {
            md.update(p.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
        }
        return HexFormat.of().formatHex(md.digest()).substring(0, 16);
    }
}
