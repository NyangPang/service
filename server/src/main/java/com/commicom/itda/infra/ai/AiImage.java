package com.commicom.itda.infra.ai;

import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * AI 모델에 보낼 이미지 준비.
 * 실제 형식과 다른 MIME 타입을 붙이면 Anthropic API가 거절하므로, 형식을 확인해서 맞게 붙인다.
 */
public final class AiImage {

    /** Claude 비전 권장 최대 변 길이 — 더 크면 줄여서 보냄 */
    public static final int MAX_SIDE_PX = 1568;
    /** 줄이지 못하고 그대로 보내는 이미지(WebP·GIF)의 최대 크기 — 이미지 한도(약 3.75MB)보다 약간 작게 */
    public static final int MAX_PASS_THROUGH_BYTES = 3_500_000;

    private AiImage() {}

    /**
     * 긴 변을 {@link #MAX_SIDE_PX} 이하로 줄이고 JPEG로 바꿔 Media로 감싼다.
     * 자바가 못 여는 WebP·GIF는 줄이지 않고 그대로 보낸다 (모델은 둘 다 읽음).
     */
    public static Media toMedia(byte[] bytes) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) {
                MimeType passThrough = sniffPassThroughType(bytes);
                if (passThrough == null) {
                    throw new IllegalArgumentException("읽을 수 없는 이미지 형식이에요 (jpg, png, webp, gif만 가능)");
                }
                if (bytes.length > MAX_PASS_THROUGH_BYTES) {
                    throw new IllegalArgumentException("이미지가 너무 커요 (webp·gif는 3.5MB까지)");
                }
                return new Media(passThrough, new ByteArrayResource(bytes));
            }
            double scale = Math.min(1.0, (double) MAX_SIDE_PX / Math.max(src.getWidth(), src.getHeight()));
            int w = (int) Math.round(src.getWidth() * scale);
            int h = (int) Math.round(src.getHeight() * scale);

            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            try (var ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(out.toByteArray()));
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지를 읽지 못했어요: " + e.getMessage(), e);
        }
    }

    /** 파일 앞부분(매직 넘버)으로 WebP·GIF를 알아낸다. 그 밖에는 null */
    static MimeType sniffPassThroughType(byte[] b) {
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return MimeType.valueOf("image/webp");
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return MimeTypeUtils.IMAGE_GIF;
        }
        return null;
    }
}
