package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * B 파트 "이미지 생성" 단계 (스키마 3절):
 * 메뉴 추출 → <b>이미지 생성</b> → 사장님 확인 → 장면 JSON → 영상
 *
 * 메뉴 데이터의 image_requests 마다 Runway로 이미지 1장을 만들어
 * photos[] 끝에 { source: generated, kind: food, menu_ref } 로 붙이고, 사진 파일 목록도 같은 순서로 붙인다.
 * 다 만들면 image_requests 는 [] 로 비운다 (같은 결과로 다시 돌려도 또 만들지 않게).
 *
 * 같은 프롬프트·해상도는 캐시에서 재사용하므로 재시도나 테스트에 크레딧이 다시 들지 않는다.
 * 메뉴 데이터는 JSON(ObjectNode)으로 받는다 — A 파트 레코드에 묶이지 않고 스키마만 맞으면 된다.
 */
public final class MenuImageGenerator {

    public record Generated(String menuRef, String prompt, Path image, boolean cached) {}

    private record Pending(String menuRef, String prompt, Path image, boolean cached) {}

    /**
     * @param menu   photos[] 에 생성 이미지가 붙고 image_requests 가 빈 메뉴 데이터
     * @param photos 업로드 사진 + 생성 이미지 (photos[].index 순서 = 영상 photoUrls 순서)
     */
    public record Result(ObjectNode menu, List<Path> photos, List<Generated> generated, int creditsSpent) {}

    private final RunwayImageClient runway;
    private final Path cacheDir;
    private final String ratio;
    private final Consumer<String> log;

    MenuImageGenerator(RunwayImageClient runway, Path cacheDir, boolean hd, Consumer<String> log) throws Exception {
        this.runway = runway;
        this.cacheDir = Files.createDirectories(cacheDir);
        this.ratio = hd ? RunwayImageClient.RATIO_HD : RunwayImageClient.RATIO_SD;
        this.log = log;
    }

    /**
     * @param menu           메뉴 추출 결과 (그대로 두고 복사본을 고쳐서 돌려준다)
     * @param uploadedPhotos 사장님 사진 파일, photos[] 와 같은 순서·같은 장수
     */
    public Result generate(ObjectNode menu, List<Path> uploadedPhotos) throws Exception {
        ObjectNode out = menu.deepCopy();
        ArrayNode photos = out.withArray("photos");
        if (photos.size() != uploadedPhotos.size()) {
            throw new IllegalArgumentException("사진 파일이 " + uploadedPhotos.size() + "장인데 photos[]는 "
                    + photos.size() + "장이에요. 같은 순서로 같은 장수를 넣어 주세요.");
        }
        List<Path> files = new ArrayList<>(uploadedPhotos);
        List<Generated> generated = new ArrayList<>();
        int credits = 0;

        JsonNode requests = out.path("image_requests");
        if (requests.isArray() && !requests.isEmpty()) {
            if (!runway.enabled()) {
                throw new IllegalStateException("이미지 생성 요청이 " + requests.size() + "개 있는데 RUNWAYML_API_SECRET 이 없어요.");
            }
            // 캐시에 없는 이미지는 모두 동시에 요청 (하나씩 기다리면 요청 수만큼 시간이 늘어남)
            List<Pending> pending = new ArrayList<>();
            Map<Path, Future<?>> jobs = new LinkedHashMap<>();
            ExecutorService pool = Executors.newCachedThreadPool();
            try {
                for (JsonNode req : requests) {
                    // 여러 메뉴를 함께 그린 이미지는 menu_ref가 null (가격 자막 없이 씀)
                    String menuRef = req.path("menu_ref").isTextual() ? req.path("menu_ref").asText() : null;
                    String prompt = req.path("prompt").asText();
                    Path image = cacheDir.resolve("img-" + hash(RunwayImageClient.MODEL, ratio, prompt) + ".png");
                    boolean cached = Files.exists(image) || jobs.containsKey(image);
                    if (!cached) {
                        jobs.put(image, pool.submit(() -> {
                            Path tmp = image.resolveSibling(image.getFileName() + ".part.png");
                            try {
                                runway.generate(prompt, ratio, tmp);
                                Files.move(tmp, image, StandardCopyOption.REPLACE_EXISTING);
                            } finally {
                                Files.deleteIfExists(tmp);
                            }
                            return null;
                        }));
                        credits += RunwayImageClient.credits(ratio);
                    }
                    pending.add(new Pending(menuRef, prompt, image, cached));
                }
                if (!jobs.isEmpty()) log.accept("  이미지 " + jobs.size() + "장 동시 요청");
                Exception failure = null;
                for (Future<?> job : jobs.values()) {
                    try {
                        job.get();
                    } catch (ExecutionException ex) {
                        Exception cause = ex.getCause() instanceof Exception c ? c : ex;
                        if (failure == null) failure = cause;
                        else failure.addSuppressed(cause);
                    }
                }
                if (failure != null) throw failure;   // 성공한 이미지는 캐시에 남아 다시 돌릴 때 크레딧이 들지 않음
            } finally {
                pool.shutdownNow();
            }

            for (Pending p : pending) {
                String menuRef = p.menuRef();
                String prompt = p.prompt();
                Path image = p.image();
                boolean cached = p.cached();
                int index = photos.size();
                String label = menuRef == null ? "메뉴 조합" : menuRef;
                log.accept("  " + label + " 이미지 " + (cached ? "재사용" : "새로 만듦") + " → photos[" + index + "]");

                ObjectNode photo = photos.addObject()
                        .put("index", index)
                        .put("source", "generated")
                        .put("kind", "food");
                if (menuRef == null) photo.putNull("menu_ref");
                else photo.put("menu_ref", menuRef);
                photo.put("description", "AI가 만든 " + label + " 이미지: " + prompt);
                files.add(image);
                generated.add(new Generated(menuRef, prompt, image, cached));
            }
        }
        out.putArray("image_requests");
        return new Result(out, files, generated, credits);
    }

    private static String hash(String... parts) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String p : parts) {
            md.update(String.valueOf(p).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
        }
        return HexFormat.of().formatHex(md.digest()).substring(0, 16);
    }
}
