package com.commicom.itda.infra.video.storyboard;

import com.commicom.itda.domain.generation.storyboard.CaptionSplitter;
import com.commicom.itda.infra.video.storyboard.Storyboard.Caption;
import com.commicom.itda.infra.video.storyboard.Storyboard.Style;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

/**
 * 장면 자막 — "미니멀 매거진" 스타일.
 * 화면 폭에 맞춘 한 줄 자막을 투명 PNG로 그린다. 표시 시간은 FFmpeg에서 내레이션에 맞춘다.
 */
final class CaptionRenderer {

    static final int W = 1080;
    static final int H = 1920;

    private static final int LEFT = 80;
    private static final int MAX_WIDTH = W - LEFT * 2;
    /** bottom 위치 자막 블록의 위 끝 — 화면 가운데 바로 아래 (쇼츠 앱 설명·버튼 영역보다 한참 위) */
    private static final int BOTTOM_TOP = H / 2 + 80;
    private static final int TOP_EDGE = 300;

    private final Font extraBold;
    private final Font semiBold;

    CaptionRenderer() {
        this.extraBold = loadFont("/fonts/Pretendard-ExtraBold.otf");
        this.semiBold = loadFont("/fonts/Pretendard-SemiBold.otf");
    }

    // ── 상자 템플릿 (VideoTemplate.HEADLINE) ──
    static final Color YELLOW = new Color(255, 211, 74);
    static final Color DARK = new Color(17, 17, 17);
    /** 상자 안 글자 양옆 여백 */
    private static final int BOX_PAD_X = 28;
    /** 상자 높이 = 글자 크기 × 이 값 */
    private static final float BOX_HEIGHT = 1.5f;
    private static final float HEADLINE_CAPTION_SIZE = 52f;
    /** 꽉 찬 화면형 자막 상자의 위 끝 — 화면 아래쪽(64%), 피드 버튼·설명 영역보다 위. 가운데는 비워 둔다 */
    private static final int HEADLINE_CAPTION_TOP = 1230;

    // ── 잇다 로고 색 (꽉 찬 화면형). 글씨색은 배경 대비 4.5 이상 되게 고름 ──
    /** 상단 메인 제목 상자 (흰 글씨, 대비 7.0) */
    static final Color BRAND_DEEP = new Color(0x086816);
    /** 상단 윗줄 상자 (검정 글씨, 대비 7.9) */
    static final Color BRAND_MID = new Color(0x3CB371);
    /** 하단 자막 상자 (검정 글씨, 대비 14.2) */
    static final Color BRAND_LIGHT = new Color(0xCBD7CE);
    private static final Color BRAND_INK = new Color(17, 17, 17);

    // ── 위아래 띠형 (VideoTemplate.BAND) / 카드형 (VideoTemplate.CARD) ──
    static final Color CREAM = new Color(255, 246, 234);
    static final Color ORANGE = new Color(255, 122, 0);
    private static final Color CARD_INK = new Color(43, 33, 24);
    private static final Color CARD_SUB = new Color(90, 70, 55);
    private static final Color HIGHLIGHT = new Color(255, 200, 150);
    /** 띠형 자막 블록 위 끝 (아래 띠 안) */
    private static final int BAND_CAPTION_TOP = 1490;
    private static final int CARD_RADIUS = 56;

    /**
     * 한 장면 자막을 화면에 차례로 띄울 조각으로 나눈다 (띄어쓰기 단위, 조각마다 18자 안팎, 최대 3조각 — {@link CaptionSplitter}).
     * 꽉 찬 화면형(A안)은 글씨 크기가 고정이라, 그래도 폭을 넘는 조각(띄어쓰기 없는 긴 말)은 폭에 맞춰 한 번 더 나눈다.
     */
    List<String> captionLines(String text, VideoTemplate template) {
        String line = text.strip().replaceAll("\\s+", " ");
        if (line.isBlank()) return List.of(line);
        List<String> parts = CaptionSplitter.split(line);
        if (template != VideoTemplate.HEADLINE) return parts;
        List<String> lines = new ArrayList<>();
        for (String part : parts) lines.addAll(fitHeadlineWidth(part));
        return lines;
    }

    private List<String> fitHeadlineWidth(String line) {
        AttributedString styled = new AttributedString(line);
        styled.addAttribute(TextAttribute.FONT, extraBold.deriveFont(HEADLINE_CAPTION_SIZE));
        LineBreakMeasurer measure = new LineBreakMeasurer(styled.getIterator(), new FontRenderContext(null, true, true));
        List<String> lines = new ArrayList<>();
        while (measure.getPosition() < line.length()) {
            int start = measure.getPosition();
            int end = measure.nextOffset(MAX_WIDTH);
            lines.add(line.substring(start, end).strip());
            measure.setPosition(end);
        }
        return lines;
    }

    void renderCaption(Caption caption, Style style, VideoTemplate template, Path png) throws IOException {
        String text = caption.text();
        String position = caption.position() == null ? "bottom" : caption.position();
        boolean classic = template == VideoTemplate.CLASSIC;
        boolean large = "large".equals(caption.emphasis());
        boolean bar = classic && (large || "price".equals(caption.emphasis()));
        float size = switch (template) {
            case HEADLINE -> HEADLINE_CAPTION_SIZE;
            case BAND -> large ? 96 : 70;
            case CARD -> large ? 80 : 62;
            case CLASSIC -> large ? 112 : 76;
        };

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontRenderContext frc = g.getFontRenderContext();
        boolean regular = classic && !bar && style != null && "gothic_regular".equals(style.font());
        Font font = (regular ? semiBold : extraBold).deriveFont(size);
        double advance = width(text, font, frc);
        if (template != VideoTemplate.HEADLINE && advance > MAX_WIDTH) {
            font = font.deriveFont((float) (size * MAX_WIDTH / advance));
        }
        TextLayout line = new TextLayout(text, font, frc);
        float height = font.getSize2D() * switch (template) {
            case HEADLINE -> BOX_HEIGHT;
            case CARD -> 1.8f;
            default -> 1.2f;
        };
        boolean centered = !classic || "center".equals(position);
        float x = centered ? (W - line.getAdvance()) / 2f : LEFT;
        float blockHeight = height + (bar ? 52 : 0);
        float y = switch (template) {
            case HEADLINE -> HEADLINE_CAPTION_TOP;
            case BAND -> BAND_CAPTION_TOP;
            case CARD -> template.photoBox.y() + template.photoBox.h() - height / 2f;
            case CLASSIC -> switch (position) {
                case "top" -> TOP_EDGE;
                case "center" -> (H - blockHeight) / 2f;
                default -> BOTTOM_TOP;
            };
        };

        if (classic) {
            g.drawImage(shade(position), 0, 0, null);
            if (bar) {
                Color accent = lighten(parseColor(style == null ? null : style.mainColor(), new Color(0x5E8C61)), 0.65);
                g.setColor(accent);
                g.fillRect(centered ? (W - 90) / 2 : LEFT, Math.round(y), 90, 10);
                y += 52;
            }
            float baseline = y + line.getAscent();
            g.setColor(new Color(0, 0, 0, 110));
            line.draw(g, x + 2, baseline + 4);
            g.setColor(Color.WHITE);
            line.draw(g, x, baseline);
        } else if (template == VideoTemplate.BAND) {
            g.setColor(Color.WHITE);
            int price = priceStart(text);
            float baseline = y + line.getAscent();
            if (price < 0) {
                line.draw(g, x, baseline);
            } else {
                g.setFont(font);
                String name = text.substring(0, price);
                g.drawString(name, x, baseline);
                g.setColor(YELLOW);
                g.drawString(text.substring(price), x + (float) width(name, font, frc), baseline);
            }
        } else if (template == VideoTemplate.CARD) {
            drawBoxed(g, text, font, Color.WHITE, ORANGE, x, y, height, height);
        } else {
            drawBoxed(g, text, font, BRAND_INK, BRAND_LIGHT, x, y, height);
        }
        g.dispose();
        ImageIO.write(img, "png", png.toFile());
    }

    /** "소금빵 3,500원"처럼 끝이 가격이면 가격이 시작하는 위치, 아니면 -1 */
    private static int priceStart(String text) {
        int sp = text.lastIndexOf(' ');
        return sp > 0 && text.substring(sp + 1).matches("[0-9,]+원") ? sp + 1 : -1;
    }

    /** (x, y)에서 시작하는 글자 줄 뒤에 상자를 깔고, 글자를 상자 높이 가운데에 쓴다. x는 글자 시작점 (상자는 양옆으로 BOX_PAD_X 더 넓음) */
    private static void drawBoxed(Graphics2D g, String text, Font font, Color ink, Color box, float x, float y, float height) {
        drawBoxed(g, text, font, ink, box, x, y, height, 0);
    }

    /** arc > 0 이면 둥근 상자 (height를 주면 알약 모양) */
    private static void drawBoxed(Graphics2D g, String text, Font font, Color ink, Color box, float x, float y, float height, float arc) {
        TextLayout tl = new TextLayout(text, font, g.getFontRenderContext());
        g.setColor(box);
        int pad = arc > 0 ? Math.round(BOX_PAD_X * 1.7f) : BOX_PAD_X;
        int bx = Math.round(x - pad), by = Math.round(y), bw = Math.round(tl.getAdvance() + pad * 2), bh = Math.round(height);
        if (arc > 0) g.fillRoundRect(bx, by, bw, bh, Math.round(arc), Math.round(arc));
        else g.fillRect(bx, by, bw, bh);
        // 글자 실제 모양(잉크) 기준으로 세로 가운데
        var b = tl.getBounds();
        float baseline = (float) (y + height / 2f - (b.getY() + b.getHeight() / 2));
        g.setColor(ink);
        tl.draw(g, x, baseline);
    }

    /**
     * 템플릿의 고정 머리 (영상 내내 그대로 얹는 투명 PNG).
     * 제목은 가운데에 가까운 띄어쓰기에서 두 줄로 나눠 윗줄은 작게, 아랫줄은 크게 (9자 이하거나 띄어쓰기가 없으면 한 줄).
     * <ul>
     *   <li>headline: 위쪽 살짝 어둡게 + 왼쪽 위 가게 이름 태그 + 제목 상자 (윗줄 검은 상자·흰 글씨, 아랫줄 노란 상자·검은 글씨)</li>
     *   <li>band: 위 띠 안에 제목 (윗줄 흰 글씨, 아랫줄 노란 글씨)</li>
     *   <li>card: 제목 (윗줄 갈색, 아랫줄 진한 글씨 + 형광펜 밑줄)</li>
     * </ul>
     */
    void renderHeader(VideoTemplate template, String title, String storeName, Path png) throws IOException {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontRenderContext frc = g.getFontRenderContext();
        List<String> lines = titleLines(title, frc);

        switch (template) {
            case BAND -> {
                float y = centeredBlockTop(lines, 70, 104, 1.22f, template.photoBox.y());
                if (lines.size() == 2) y += drawCentered(g, lines.get(0), fit(semiBold, 70, lines.get(0), frc), Color.WHITE, y) * 1.22f;
                if (!lines.isEmpty()) drawCentered(g, last(lines), fit(extraBold, 104, last(lines), frc), YELLOW, y);
            }
            case CARD -> {
                float y = centeredBlockTop(lines, 66, 100, 1.22f, template.photoBox.y());
                if (lines.size() == 2) y += drawCentered(g, lines.get(0), fit(semiBold, 66, lines.get(0), frc), CARD_SUB, y) * 1.22f;
                if (!lines.isEmpty()) {
                    String main = last(lines);
                    Font f = fit(extraBold, 100, main, frc);
                    TextLayout tl = new TextLayout(main, f, frc);
                    // 형광펜: 글자 아래쪽 40%에 걸치게
                    var b = tl.getBounds();
                    float baseline = y + tl.getAscent();
                    float hlTop = (float) (baseline + b.getY() + b.getHeight() * 0.55);
                    g.setColor(HIGHLIGHT);
                    g.fillRect(Math.round((W - tl.getAdvance()) / 2f - 12), Math.round(hlTop), Math.round(tl.getAdvance() + 24),
                            Math.round(baseline + 8 - hlTop));
                    drawCentered(g, main, f, CARD_INK, y);
                }
            }
            default -> {
                // 밝은 사진에서도 태그·제목 주변이 들뜨지 않게 위쪽만 살짝
                for (int y = 0; y < HEADER_SHADE; y++) {
                    g.setColor(new Color(0, 0, 0, Math.round(90f * (1 - y / (float) HEADER_SHADE))));
                    g.fillRect(0, y, W, 1);
                }
                if (storeName != null && !storeName.isBlank()) {
                    String name = storeName.strip();
                    TextLayout tl = new TextLayout(name, semiBold.deriveFont(40f), frc);
                    int h = 72;
                    g.setColor(new Color(0, 0, 0, 150));
                    g.fillRoundRect(60, 110, Math.round(tl.getAdvance()) + 56, h, h, h);
                    var b = tl.getBounds();
                    g.setColor(Color.WHITE);
                    tl.draw(g, 88, (float) (110 + h / 2f - (b.getY() + b.getHeight() / 2)));
                }
                float y = HEADER_TITLE_TOP;
                if (lines.size() == 2) {
                    Font f1 = fit(extraBold, 70, lines.get(0), frc);
                    float h1 = f1.getSize2D() * BOX_HEIGHT;
                    drawBoxed(g, lines.get(0), f1, BRAND_INK, BRAND_MID, (W - (float) width(lines.get(0), f1, frc)) / 2f, y, h1);
                    y += h1;
                }
                if (!lines.isEmpty()) {
                    String main = last(lines);
                    Font f2 = fit(extraBold, 96, main, frc);
                    drawBoxed(g, main, f2, Color.WHITE, BRAND_DEEP, (W - (float) width(main, f2, frc)) / 2f, y, f2.getSize2D() * BOX_HEIGHT);
                }
            }
        }
        g.dispose();
        Files.createDirectories(png.toAbsolutePath().getParent());
        ImageIO.write(img, "png", png.toFile());
    }

    /**
     * 사진 칸이 화면 일부인 템플릿의 바탕 (장면마다 클립 아래·위에 깜).
     * under: 클립 아래 (band 검정 / card 크림색 + 카드 그림자). over: 클립 위 (card만 — 카드 바깥 모서리를 크림색으로 덮어 둥글게). 없으면 over는 만들지 않음
     */
    void renderBackdrop(VideoTemplate template, Path under, Path over) throws IOException {
        VideoTemplate.Box box = template.photoBox;
        BufferedImage u = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = u.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(template == VideoTemplate.CARD ? CREAM : DARK);
        g.fillRect(0, 0, W, H);
        if (template == VideoTemplate.CARD) {
            // 부드러운 그림자: 조금씩 넓힌 둥근 사각형을 옅게 여러 번
            for (int i = 28; i > 0; i -= 2) {
                g.setColor(new Color(80, 50, 20, 6));
                g.fillRoundRect(box.x() - i, box.y() + 24 - i, box.w() + i * 2, box.h() + i * 2, CARD_RADIUS * 2 + i, CARD_RADIUS * 2 + i);
            }
        }
        g.dispose();
        Files.createDirectories(under.toAbsolutePath().getParent());
        ImageIO.write(u, "png", under.toFile());
        if (template != VideoTemplate.CARD) return;

        BufferedImage o = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D go = o.createGraphics();
        go.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        go.setColor(CREAM);
        go.fillRect(box.x(), box.y(), box.w(), box.h());
        go.setComposite(AlphaComposite.DstOut);
        go.fillRoundRect(box.x(), box.y(), box.w(), box.h(), CARD_RADIUS * 2, CARD_RADIUS * 2);
        go.dispose();
        ImageIO.write(o, "png", over.toFile());
    }

    private List<String> titleLines(String title, FontRenderContext frc) {
        if (title == null || title.isBlank()) return List.of();
        String t = title.strip();
        return t.replace(" ", "").length() <= 9 ? List.of(t) : wrap(t, extraBold.deriveFont(96f), frc, 0);
    }

    /** 제목 블록을 0~bottom 사이 세로 가운데에 놓을 때 위 끝 */
    private static float centeredBlockTop(List<String> lines, float small, float big, float lineHeight, int bottom) {
        float h = (lines.size() == 2 ? small * lineHeight : 0) + big * lineHeight;
        return (bottom - h) / 2f + 20;
    }

    /** 가운데 정렬로 y(위 끝)에 쓰고 글자 크기를 돌려줌 */
    private static float drawCentered(Graphics2D g, String text, Font font, Color color, float y) {
        TextLayout tl = new TextLayout(text, font, g.getFontRenderContext());
        g.setColor(color);
        tl.draw(g, (W - tl.getAdvance()) / 2f, y + tl.getAscent());
        return font.getSize2D();
    }

    private static String last(List<String> lines) {
        return lines.get(lines.size() - 1);
    }

    private static final int HEADER_SHADE = 760;
    private static final int HEADER_TITLE_TOP = 232;

    /** 너무 길면(글자 수 > softMax 또는 폭 초과) 가운데에 가장 가까운 띄어쓰기에서 두 줄로 */
    private static List<String> wrap(String text, Font font, FontRenderContext frc, int softMax) {
        boolean tooLong = text.replace(" ", "").length() > softMax || width(text, font, frc) > MAX_WIDTH;
        if (!tooLong || !text.contains(" ")) return List.of(text);
        int mid = text.length() / 2;
        int best = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i;
        }
        return List.of(text.substring(0, best).strip(), text.substring(best + 1).strip());
    }

    /** 폭을 넘으면 글자 크기를 줄임 */
    private static Font fit(Font base, float size, String text, FontRenderContext frc) {
        Font f = base.deriveFont(size);
        while (width(text, f, frc) > MAX_WIDTH && size > 44) {
            size -= 4;
            f = base.deriveFont(size);
        }
        return f;
    }

    /** 글자가 놓이는 쪽을 어둡게 하는 그라데이션 */
    private static BufferedImage shade(String position) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < H; y++) {
            double a = switch (position) {
                case "top" -> ramp((H * 0.62 - y) / (H * 0.62));
                case "center" -> 0.62 * Math.pow(Math.max(0, 1 - Math.abs(y - H / 2.0) / (H * 0.34)), 0.8);
                default -> ramp((y - H * 0.34) / (H * 0.5));
            };
            g.setColor(new Color(0, 0, 0, (int) Math.round(255 * a)));
            g.fillRect(0, y, W, 1);
        }
        g.dispose();
        return img;
    }

    private static double ramp(double t) {
        t = Math.max(0, t);
        return 0.94 * Math.pow(Math.min(1.0, t * 1.3), 1.2);
    }

    private static double width(String s, Font font, FontRenderContext frc) {
        return new TextLayout(s, font, frc).getAdvance();
    }

    private static Color lighten(Color c, double amount) {
        return new Color(
                (int) (c.getRed() + (255 - c.getRed()) * amount),
                (int) (c.getGreen() + (255 - c.getGreen()) * amount),
                (int) (c.getBlue() + (255 - c.getBlue()) * amount));
    }

    private static Color parseColor(String hex, Color fallback) {
        try {
            return hex == null ? fallback : Color.decode(hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Font loadFont(String resource) {
        try (InputStream in = CaptionRenderer.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("폰트 없음: " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("폰트 로드 실패: " + resource, e);
        }
    }
}
