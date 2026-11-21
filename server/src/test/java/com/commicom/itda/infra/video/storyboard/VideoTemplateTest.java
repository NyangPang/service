package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTemplateTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void 주제와_상관없이_기본은_headline() {
        for (String theme : new String[]{"value", "expert", "emotional", "fresh", "trendy", null}) {
            assertThat(VideoTemplate.forTheme(theme)).isEqualTo(VideoTemplate.HEADLINE);
        }
    }

    @Test
    void meta_template이_있으면_주제보다_우선() throws Exception {
        Storyboard sb = json.readValue("""
                {"title":"t","theme":"value","scenes":[],"meta":{"template":" Classic ","store_name":" 골목카페 "}}""", Storyboard.class);
        assertThat(sb.template()).isEqualTo(VideoTemplate.CLASSIC);
        assertThat(sb.storeName()).isEqualTo("골목카페");

        Storyboard byTheme = json.readValue("""
                {"title":"t","theme":"value","scenes":[],"meta":{"template":"nope"}}""", Storyboard.class);
        assertThat(byTheme.template()).isEqualTo(VideoTemplate.HEADLINE);

        Storyboard noMeta = json.readValue("{\"title\":\"t\",\"scenes\":[]}", Storyboard.class);
        assertThat(noMeta.template()).isEqualTo(VideoTemplate.HEADLINE);
        assertThat(noMeta.storeName()).isNull();
    }

    @Test
    void 고정_제목은_위쪽에_로고색_상자로_그려짐(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("header.png");
        new CaptionRenderer().renderHeader(VideoTemplate.HEADLINE, "퇴근길 달달할 땐 골목 끝 소금빵", "골목카페", png);

        BufferedImage img = ImageIO.read(png.toFile());
        assertThat(img.getWidth()).isEqualTo(CaptionRenderer.W);
        assertThat(img.getHeight()).isEqualTo(CaptionRenderer.H);
        // 윗줄 중간 초록, 아랫줄(메인) 진한 초록 상자
        assertThat(hasColor(img, CaptionRenderer.BRAND_MID, 230, 340)).isTrue();
        assertThat(hasColor(img, CaptionRenderer.BRAND_DEEP, 300, 460)).isTrue();
        // 화면 아래쪽은 투명 (영상이 그대로 보임)
        assertThat(new Color(img.getRGB(540, 1500), true).getAlpha()).isZero();
    }

    @Test
    void 꽉_찬_화면형의_본문은_위치와_강조_설정에_관계없이_아래쪽_연한_초록_상자(@TempDir Path dir) throws Exception {
        CaptionRenderer renderer = new CaptionRenderer();
        Path png = dir.resolve("caption.png");
        Path large = dir.resolve("large.png");
        renderer.renderCaption(new Storyboard.Caption("소금빵 3,500원", "bottom", "price"), null,
                VideoTemplate.HEADLINE, png);
        renderer.renderCaption(new Storyboard.Caption("소금빵 3,500원", "top", "large"), null,
                VideoTemplate.HEADLINE, large);
        assertThat(Files.readAllBytes(png)).containsExactly(Files.readAllBytes(large));
        BufferedImage img = ImageIO.read(png.toFile());
        assertThat(new Color(img.getRGB(20, 1700), true).getAlpha()).isZero();
        assertThat(hasColor(img, CaptionRenderer.BRAND_LIGHT, 1230, 1310)).isTrue();
        // 화면 가운데는 비어 있음 (위 제목과 아래 자막만)
        assertThat(hasColor(img, CaptionRenderer.BRAND_LIGHT, 600, 1200)).isFalse();
        assertThat(new Color(img.getRGB(540, 960), true).getAlpha()).isZero();
    }

    @Test
    void 꽉_찬_화면형의_긴_본문은_글자를_잃지_않고_나눔(@TempDir Path dir) throws Exception {
        CaptionRenderer renderer = new CaptionRenderer();
        String text = "가나다라마바사아자차카타파하".repeat(3);
        var lines = renderer.captionLines(text, VideoTemplate.HEADLINE);
        assertThat(lines).hasSizeGreaterThan(1).noneMatch(String::isBlank);
        assertThat(String.join("", lines)).isEqualTo(text);
        Path png = dir.resolve("long.png");
        renderer.renderCaption(new Storyboard.Caption(lines.getFirst(), "center", "normal"), null,
                VideoTemplate.HEADLINE, png);
        BufferedImage img = ImageIO.read(png.toFile());
        assertThat(new Color(img.getRGB(0, 960), true).getAlpha()).isZero();
        assertThat(new Color(img.getRGB(img.getWidth() - 1, 960), true).getAlpha()).isZero();
    }

    @Test
    void 긴_자막은_모든_템플릿에서_띄어쓰기_단위_조각으로_나뉨() {
        CaptionRenderer renderer = new CaptionRenderer();
        for (VideoTemplate t : VideoTemplate.values()) {
            assertThat(renderer.captionLines("딸기 복숭아 샤인머스켓 골라 먹는 재미예요.", t))
                    .as(t.id()).containsExactly("딸기 복숭아 샤인머스켓", "골라 먹는 재미예요.");
        }
    }

    @Test
    void 꽉_찬_화면형에서_18자_조각은_폭_때문에_다시_나뉘지_않음() {
        // CaptionSplitter.PART_MAX(18자)가 꽉 찬 화면형 글씨 크기에서 한 줄에 들어가야 조각이 의도대로 보인다
        String widest = "가나다라마바사아자 차카타파하가나다";
        assertThat(widest).hasSize(18);
        assertThat(new CaptionRenderer().captionLines(widest, VideoTemplate.HEADLINE)).containsExactly(widest);
    }

    @Test
    void 띠형에서_긴_자막도_한_줄이고_제목은_아래_띠에_그리지_않음(@TempDir Path dir) throws Exception {
        CaptionRenderer renderer = new CaptionRenderer();
        Path caption = dir.resolve("caption.png");
        renderer.renderCaption(new Storyboard.Caption("직접 내려주는 커피라 맛이 달라요.", "bottom", "normal"),
                null, VideoTemplate.BAND, caption);
        BufferedImage line = ImageIO.read(caption.toFile());
        assertThat(hasColor(line, Color.WHITE, 1490, 1574)).isTrue();
        assertThat(hasColor(line, Color.WHITE, 1574, 1680)).isFalse();

        Path header = dir.resolve("header.png");
        renderer.renderHeader(VideoTemplate.BAND, "월계동 카페는 바리스타 커피", "광운 카페", header);
        BufferedImage title = ImageIO.read(header.toFile());
        assertThat(hasColor(title, CaptionRenderer.YELLOW, 0, 540)).isTrue();
        assertThat(hasColor(title, new Color(170, 170, 170), 1460, 1920)).isFalse();
    }

    @Test
    void 카드형_바탕은_크림색이고_모서리_덮개가_있음(@TempDir Path dir) throws Exception {
        Path under = dir.resolve("under.png");
        Path over = dir.resolve("over.png");
        new CaptionRenderer().renderBackdrop(VideoTemplate.CARD, under, over);
        BufferedImage u = ImageIO.read(under.toFile());
        assertThat(u.getRGB(10, 10) & 0xFFFFFF).isEqualTo(CaptionRenderer.CREAM.getRGB() & 0xFFFFFF);
        BufferedImage o = ImageIO.read(over.toFile());
        VideoTemplate.Box box = VideoTemplate.CARD.photoBox;
        // 카드 왼쪽 위 모서리 끝은 덮고, 카드 가운데는 비워 둠
        assertThat(new Color(o.getRGB(box.x() + 2, box.y() + 2), true).getAlpha()).isEqualTo(255);
        assertThat(new Color(o.getRGB(box.x() + box.w() / 2, box.y() + box.h() / 2), true).getAlpha()).isZero();
    }

    @Test
    void 띠형_바탕은_덮개가_없음(@TempDir Path dir) throws Exception {
        Path over = dir.resolve("over.png");
        new CaptionRenderer().renderBackdrop(VideoTemplate.BAND, dir.resolve("under.png"), over);
        assertThat(over).doesNotExist();
    }

    /** y0~y1 줄 안에 그 색 픽셀이 있는지 */
    private static boolean hasColor(BufferedImage img, Color c, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == (c.getRGB() & 0xFFFFFF) && (img.getRGB(x, y) >>> 24) == 255) return true;
            }
        }
        return false;
    }
}
