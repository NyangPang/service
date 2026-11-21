package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runway 영상 클립.
 * <ul>
 *   <li>{@link #generate}: Gen-4 Turbo — 사진 1장 + 장면 video_prompt → 5초 세로 클립. 5 credits/초 → 25 credits</li>
 *   <li>{@link #generateTransition}: Kling 2.5 Turbo Pro — 첫 화면·끝 화면 사진 2장 + 프롬프트 → 그 사이를 잇는 5초 클립.
 *       Gen-4 Turbo는 첫 화면만 받아서 끝 화면(가게 외관)을 정할 수 없다. 12 credits/초 → 60 credits</li>
 * </ul>
 * 사진은 720x1280 JPEG로 줄여 data URI로 보낸다 (S3 주소가 없어도 됨).
 */
final class RunwayClipClient {

    private static final String BASE_URL = "https://api.dev.runwayml.com/v1";
    private static final String API_VERSION = "2024-11-06";
    private static final String MODEL = "gen4_turbo";
    /** 처음·끝 화면(promptImage position first/last)을 둘 다 받는 모델. ratio는 받지 않고 입력 사진 비율을 따른다 */
    static final String TRANSITION_MODEL = "kling2.5_turbo_pro";
    /** 두 모델 모두 5초 또는 10초만 받는다 */
    static final int CLIP_SECONDS = 5;
    static final int LONG_CLIP_SECONDS = 10;
    private static final Duration POLL = Duration.ofSeconds(5);
    /**
     * 여러 클립을 동시에 보내면 Runway가 동시 실행 한도만큼만 돌리고 나머지는 대기(THROTTLED)시키므로 넉넉히.
     * Kling 클립은 하나에 4~5분 걸려, 원테이크 영상(전환 3개)은 한도 대기까지 10분을 넘길 수 있다
     */
    private static final Duration MAX_WAIT = Duration.ofMinutes(20);

    private final String apiKey;
    private final Consumer<String> log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper json = new ObjectMapper();

    RunwayClipClient(String apiKey, Consumer<String> log) {
        this.apiKey = apiKey;
        this.log = log;
    }

    boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    void generate(Path photo, String prompt, Path outMp4, Ffmpeg ff) throws Exception {
        generate(photo, prompt, CLIP_SECONDS, outMp4, ff);
    }

    /** @param seconds {@link #CLIP_SECONDS} 또는 {@link #LONG_CLIP_SECONDS} */
    void generate(Path photo, String prompt, int seconds, Path outMp4, Ffmpeg ff) throws Exception {
        ObjectNode body = json.createObjectNode()
                .put("model", MODEL)
                .put("promptImage", dataUri(photo, outMp4, ff))
                .put("promptText", prompt == null ? "" : prompt)
                .put("ratio", "720:1280")
                .put("duration", seconds);
        run(body, "Runway 클립", photo.getFileName().toString(), outMp4);
    }

    /** first 사진에서 시작해 last 사진으로 끝나는 5초 클립 (예: 빵 → 가게 외관) */
    void generateTransition(Path first, Path last, String prompt, Path outMp4, Ffmpeg ff) throws Exception {
        generateTransition(first, last, prompt, CLIP_SECONDS, outMp4, ff);
    }

    /** @param seconds {@link #CLIP_SECONDS} 또는 {@link #LONG_CLIP_SECONDS} */
    void generateTransition(Path first, Path last, String prompt, int seconds, Path outMp4, Ffmpeg ff) throws Exception {
        ObjectNode body = transitionBody(json, dataUri(first, outMp4, ff), dataUri(last, outMp4, ff), prompt, seconds);
        run(body, "Runway 화면 전환", first.getFileName() + " → " + last.getFileName(), outMp4);
    }

    static ObjectNode transitionBody(ObjectMapper json, String firstUri, String lastUri, String prompt) {
        return transitionBody(json, firstUri, lastUri, prompt, CLIP_SECONDS);
    }

    static ObjectNode transitionBody(ObjectMapper json, String firstUri, String lastUri, String prompt, int seconds) {
        if (seconds != CLIP_SECONDS && seconds != LONG_CLIP_SECONDS) {
            throw new IllegalArgumentException("Runway 클립은 5초 또는 10초만 됩니다: " + seconds);
        }
        ObjectNode body = json.createObjectNode()
                .put("model", TRANSITION_MODEL)
                .put("promptText", prompt == null || prompt.isBlank() ? "smooth continuous camera move" : prompt)
                .put("duration", seconds);
        var images = body.putArray("promptImage");
        images.addObject().put("uri", firstUri).put("position", "first");
        images.addObject().put("uri", lastUri).put("position", "last");
        return body;
    }

    /** 사진 → 720x1280 JPEG data URI */
    private String dataUri(Path photo, Path outMp4, Ffmpeg ff) throws Exception {
        Path small = Files.createTempFile(outMp4.toAbsolutePath().getParent(), "runway-in-", ".jpg");
        try {
            ff.run(List.of("-i", photo.toString(),
                    "-vf", "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280",
                    "-frames:v", "1", "-q:v", "3", small.toString()));
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(small));
        } finally {
            Files.deleteIfExists(small);
        }
    }

    private void run(ObjectNode body, String label, String what, Path outMp4) throws Exception {
        JsonNode task = send(request("/image_to_video").POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        String id = task.path("id").asText();
        log.accept("    " + label + " 작업 시작 " + id + " (" + what + ", 1~3분)");
        RunwayStatusLog watch = new RunwayStatusLog(label + " " + shortId(id) + " (" + what + ")", log);

        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.sleep(POLL.toMillis());
            JsonNode status = send(request("/tasks/" + id).GET());
            watch.update(status.path("status").asText());
            switch (status.path("status").asText()) {
                case "SUCCEEDED" -> {
                    download(status.path("output").get(0).asText(), outMp4);
                    return;
                }
                case "FAILED", "CANCELLED" -> throw new IOException("Runway 생성 실패: " + status);
                default -> { }
            }
        }
        throw new IOException("Runway 시간 초과 (10분, 마지막 상태 " + watch.lastStatus() + "): " + id);
    }

    static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Runway-Version", API_VERSION)
                .header("Content-Type", "application/json");
    }

    private JsonNode send(HttpRequest.Builder req) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Runway 요청 실패 (" + res.statusCode() + "): " + res.body());
        }
        return json.readTree(res.body());
    }

    private void download(String url, Path dest) throws IOException, InterruptedException {
        HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
