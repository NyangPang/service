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
import java.util.function.Consumer;

/**
 * Runway 글 → 이미지. 음식 사진이 없을 때 메뉴 이미지를 만든다 (스키마 3절 image_requests).
 *
 * 모델은 gen4_image: 참고 이미지 없이 글만으로 만들 수 있다.
 * (gen4_image_turbo 는 2 credits로 싸지만 참고 이미지가 꼭 있어야 해서, 사진이 없는 이 경우엔 못 쓴다)
 * 비용: 720p 5 credits / 1080p 8 credits, 1장에 약 20초.
 */
class RunwayImageClient {   // 테스트에서 가짜로 바꿔 끼우려고 final 아님

    static final String MODEL = "gen4_image";
    static final String RATIO_SD = "720:1280";
    static final String RATIO_HD = "1080:1920";

    private static final String BASE_URL = "https://api.dev.runwayml.com/v1";
    private static final String API_VERSION = "2024-11-06";
    private static final Duration POLL = Duration.ofSeconds(3);
    private static final Duration MAX_WAIT = Duration.ofMinutes(6);   // 동시에 보내면 Runway가 한도를 넘는 요청을 대기시킴

    private final String apiKey;
    private final Consumer<String> log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper json = new ObjectMapper();

    RunwayImageClient(String apiKey, Consumer<String> log) {
        this.apiKey = apiKey;
        this.log = log;
    }

    boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    static int credits(String ratio) {
        return RATIO_HD.equals(ratio) ? 8 : 5;
    }

    void generate(String prompt, String ratio, Path outPng) throws Exception {
        ObjectNode body = json.createObjectNode()
                .put("model", MODEL)
                .put("promptText", prompt)
                .put("ratio", ratio);
        JsonNode task = send(request("/text_to_image").POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        String id = task.path("id").asText();
        log.accept("    Runway 이미지 작업 시작 " + id);

        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.sleep(POLL.toMillis());
            JsonNode status = send(request("/tasks/" + id).GET());
            switch (status.path("status").asText()) {
                case "SUCCEEDED" -> {
                    download(status.path("output").get(0).asText(), outPng);
                    return;
                }
                case "FAILED", "CANCELLED" -> throw new IOException("Runway 이미지 생성 실패: " + status);
                default -> { }
            }
        }
        throw new IOException("Runway 이미지 시간 초과 (6분): " + id);
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
