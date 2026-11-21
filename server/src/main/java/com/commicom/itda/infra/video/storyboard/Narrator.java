package com.commicom.itda.infra.video.storyboard;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.TextType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 장면 내레이션 → 음성 파일(wav, 44.1kHz 스테레오).
 * - polly: AWS Polly 한국어 Seoyeon (실제 서비스용, AWS 키 필요)
 * - say:   macOS 내장 음성 Yuna (키 없이 로컬 테스트용)
 */
interface Narrator {

    /** 빠르게 말하는 쇼츠 말투. 그래도 장면보다 길면 렌더러가 atempo로 더 빠르게 맞춘다 */
    double SPEED = 1.1;

    /** 캐시 키에 들어가는 이름. 목소리가 바뀌면 음성을 다시 만든다 */
    String id();

    void synthesize(String text, Path outWav, Ffmpeg ff) throws Exception;

    static Narrator polly(String accessKey, String secretKey, String region, String voice) {
        PollyClient client = PollyClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
        return new Narrator() {
            public String id() {
                return "polly:" + voice + ":r" + SPEED;
            }

            public void synthesize(String text, Path outWav, Ffmpeg ff) throws Exception {
                SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                        .text(ssml(text)).textType(TextType.SSML).voiceId(voice).languageCode("ko-KR")
                        .engine(Engine.NEURAL).outputFormat(OutputFormat.MP3).build();
                Path mp3 = outWav.resolveSibling(outWav.getFileName() + ".mp3");
                try (ResponseInputStream<SynthesizeSpeechResponse> res = client.synthesizeSpeech(req)) {
                    Files.write(mp3, res.readAllBytes());
                }
                toWav(mp3, outWav, ff);
            }
        };
    }

    /**
     * ElevenLabs — 가장 자연스러운 한국어. 기본 목소리·모델은 팀에서 고른 한국어 목소리 + eleven_v3.
     * v3는 생성할 때마다 억양이 조금씩 달라지지만, 같은 문장은 캐시로 재사용하므로 수정 때 바뀌지 않는다.
     */
    static Narrator elevenLabs(String apiKey, String voiceId, String model) {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(20)).build();
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        return new Narrator() {
            public String id() {
                return "elevenlabs:" + voiceId + ":" + model + ":s" + SPEED;
            }

            public void synthesize(String text, Path outWav, Ffmpeg ff) throws Exception {
                var res = request(text, true);
                // 모델이 speed 설정을 받지 않으면 한 번만 기본 속도로 다시 (부족한 속도는 렌더러가 atempo로 채움)
                if (res.statusCode() / 100 == 4) res = request(text, false);
                if (res.statusCode() / 100 != 2) {
                    throw new IOException("ElevenLabs 실패 (" + res.statusCode() + "): "
                            + new String(res.body(), java.nio.charset.StandardCharsets.UTF_8));
                }
                Path mp3 = outWav.resolveSibling(outWav.getFileName() + ".mp3");
                Files.write(mp3, res.body());
                toWav(mp3, outWav, ff);
            }

            private java.net.http.HttpResponse<byte[]> request(String text, boolean withSpeed) throws Exception {
                var body = json.createObjectNode().put("text", text).put("model_id", model);
                if (model.startsWith("eleven_v3") || model.contains("turbo") || model.contains("flash")) {
                    body.put("language_code", "ko");
                }
                if (withSpeed) body.putObject("voice_settings").put("speed", SPEED);
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId + "?output_format=mp3_44100_128"))
                        .timeout(java.time.Duration.ofSeconds(60))
                        .header("xi-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                return http.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            }
        };
    }

    /** Polly SSML — 말 속도만 올린다 */
    private static String ssml(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
        return "<speak><prosody rate=\"" + Math.round(SPEED * 100) + "%\">" + escaped + "</prosody></speak>";
    }

    static Narrator macSay(String voice) {
        return new Narrator() {
            public String id() {
                return "say:" + voice + ":r" + SPEED;
            }

            public void synthesize(String text, Path outWav, Ffmpeg ff) throws Exception {
                Path aiff = outWav.resolveSibling(outWav.getFileName() + ".aiff");
                Ffmpeg.exec(List.of("say", "-v", voice, "-r", String.valueOf(Math.round(175 * SPEED)), "-o", aiff.toString(), text), "say");
                toWav(aiff, outWav, ff);
            }
        };
    }

    static boolean macSayAvailable() {
        return Files.isExecutable(Path.of("/usr/bin/say"));
    }

    private static void toWav(Path in, Path outWav, Ffmpeg ff) throws IOException, InterruptedException {
        ff.run(List.of("-i", in.toString(), "-ar", "44100", "-ac", "2", outWav.toString()));
        Files.deleteIfExists(in);
    }
}
