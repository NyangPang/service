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
 * Runway Gen-4 Turbo: 사진 1장 + 장면 video_prompt → 5초 세로 클립.
 * 사진은 720x1280 JPEG로 줄여 data URI로 보낸다 (S3 주소가 없어도 됨).
 * 비용: 5 credits/초 → 5초 클립 25 credits.
 */
final class RunwayClipClient {

    private static final String BASE_URL = "https://api.dev.runwayml.com/v1";
    private static final String API_VERSION = "2024-11-06";
    private static final String MODEL = "gen4_turbo";
    private static final int CLIP_SECONDS = 5;
    private static final Duration POLL = Duration.ofSeconds(5);
    /** 여러 클립을 동시에 보내면 Runway가 동시 실행 한도만큼만 돌리고 나머지는 대기(THROTTLED)시키므로 넉넉히 */
    private static final Duration MAX_WAIT = Duration.ofMinutes(10);

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
        Path small = outMp4.resolveSibling(outMp4.getFileName() + ".jpg");
        ff.run(List.of("-i", photo.toString(),
                "-vf", "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280",
                "-frames:v", "1", "-q:v", "3", small.toString()));
        String dataUri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(small));
        Files.deleteIfExists(small);

        ObjectNode body = json.createObjectNode()
                .put("model", MODEL)
                .put("promptImage", dataUri)
                .put("promptText", prompt == null ? "" : prompt)
                .put("ratio", "720:1280")
                .put("duration", CLIP_SECONDS);
        JsonNode task = send(request("/image_to_video").POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        String id = task.path("id").asText();
        log.accept("    Runway 작업 시작 " + id + " (1~3분)");

        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.sleep(POLL.toMillis());
            JsonNode status = send(request("/tasks/" + id).GET());
            switch (status.path("status").asText()) {
                case "SUCCEEDED" -> {
                    download(status.path("output").get(0).asText(), outMp4);
                    return;
                }
                case "FAILED", "CANCELLED" -> throw new IOException("Runway 생성 실패: " + status);
                default -> { }
            }
        }
        throw new IOException("Runway 시간 초과 (10분): " + id);
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
