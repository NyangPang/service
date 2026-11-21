package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 서버에서 장면 JSON → mp4 를 만드는 입구 (B 파트를 Spring에서 부르는 곳).
 * 렌더링 자체는 {@link StoryboardRenderer}가 하고, 여기서는 설정값으로 음성·Runway·캐시 위치를 골라 준다.
 *
 * <p>장면 JSON은 문자열로 받는다. A 파트(domain.generation.storyboard)의 레코드에 묶이지 않고,
 * 스키마(docs/storyboard-schema.md)만 맞으면 어디서 만든 JSON이든 그대로 렌더링한다.
 */
@Slf4j
@Service
public class StoryboardVideoService {

    private final ObjectMapper json = new ObjectMapper();

    private final String ffmpegPath;
    private final Path workDir;
    private final String tts;
    private final VideoTemplate templateOverride;
    private final String runwayKey;
    private final String awsAccessKey;
    private final String awsSecretKey;
    private final String awsRegion;
    private final String pollyVoice;
    private final String pollyMaleVoice;
    private final String pollyMaleEngine;
    private final String elevenLabsKey;
    private final String elevenLabsVoice;
    private final String elevenLabsModel;
    private final String elevenLabsMaleVoice;

    public StoryboardVideoService(@Value("${video.ffmpeg-path}") String ffmpegPath,
                                  @Value("${video.work-dir}") String workDir,
                                  @Value("${video.tts:auto}") String tts,
                                  @Value("${video.template-override:}") String templateOverride,
                                  @Value("${runway.api-key:}") String runwayKey,
                                  @Value("${AWS_ACCESS_KEY_ID:}") String awsAccessKey,
                                  @Value("${AWS_SECRET_ACCESS_KEY:}") String awsSecretKey,
                                  @Value("${aws.region:ap-northeast-2}") String awsRegion,
                                  @Value("${aws.polly.voice-id:Seoyeon}") String pollyVoice,
                                  @Value("${aws.polly.male-voice-id:Hyunduk}") String pollyMaleVoice,
                                  @Value("${aws.polly.male-engine:generative}") String pollyMaleEngine,
                                  @Value("${ELEVENLABS_API_KEY:}") String elevenLabsKey,
                                  @Value("${ELEVENLABS_VOICE_ID:uyVNoMrnUku1dZyVEXwD}") String elevenLabsVoice,
                                  @Value("${ELEVENLABS_MODEL:eleven_v3}") String elevenLabsModel,
                                  @Value("${ELEVENLABS_MALE_VOICE_ID:}") String elevenLabsMaleVoice) {
        this.ffmpegPath = ffmpegPath;
        this.workDir = Path.of(workDir);
        this.tts = tts;
        this.templateOverride = VideoTemplate.named(templateOverride);
        if (!templateOverride.isBlank() && this.templateOverride == null) {
            throw new IllegalArgumentException("video.template-override는 headline | band | card | classic 중 하나여야 합니다");
        }
        this.runwayKey = runwayKey;
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.awsRegion = awsRegion;
        this.pollyVoice = pollyVoice;
        this.pollyMaleVoice = pollyMaleVoice;
        this.pollyMaleEngine = pollyMaleEngine;
        this.elevenLabsKey = elevenLabsKey;
        this.elevenLabsVoice = elevenLabsVoice;
        this.elevenLabsModel = elevenLabsModel;
        this.elevenLabsMaleVoice = elevenLabsMaleVoice;
    }

    /**
     * @param storyboardJson 검증을 통과한 장면 JSON
     * @param photos         photoUrls 순서와 같은 로컬 사진 (장면의 image_index가 이 목록의 순번)
     * @param useRunway      false면 키가 있어도 Runway를 부르지 않음 (motion=ai 장면도 zoom_in) — 크레딧 아끼기용
     */
    public Result render(String storyboardJson, List<Path> photos, boolean useRunway) throws Exception {
        return render(storyboardJson, photos, useRunway, NarrationVoice.DEFAULT);
    }

    /** @param voice 나레이션 목소리 (영상 스타일에 따라 남자 목소리) */
    public Result render(String storyboardJson, List<Path> photos, boolean useRunway, NarrationVoice voice) throws Exception {
        Storyboard sb = json.readValue(storyboardJson, Storyboard.class);
        String id = UUID.randomUUID().toString();
        Path out = workDir.resolve("storyboard").resolve(id).resolve("video.mp4");

        Narrator narrator = narrator(voice == null ? NarrationVoice.DEFAULT : voice);
        RunwayClipClient runway = useRunway ? new RunwayClipClient(runwayKey, log::info) : null;
        log.info("[영상] 「{}」 장면 {}개 · 사진 {}장 · 음성 {} · Runway {}", sb.title(), sb.scenes().size(), photos.size(),
                narrator == null ? "없음" : narrator.id(), runway != null && runway.enabled() ? "사용" : "안 씀(zoom_in 대체)");

        StoryboardRenderer renderer = new StoryboardRenderer(ffmpegPath, workDir.resolve("storyboard-cache"),
                narrator, runway, log::info);
        long start = System.currentTimeMillis();
        StoryboardRenderer.Result r = renderer.render(sb, photos, out, templateOverride);
        log.info("[영상] 완료 {}초 영상 ({}초 걸림) {}", String.format("%.1f", r.durationSec()),
                String.format("%.1f", (System.currentTimeMillis() - start) / 1000.0), r.video().toAbsolutePath());
        return new Result(id, r.video(), r.thumbnail(), r.durationSec(), r.scenes());
    }

    /** render()가 만든 영상 파일 위치 (id는 render 결과의 id) */
    public Path videoFile(String id) {
        return workDir.resolve("storyboard").resolve(id).resolve("video.mp4");
    }

    /**
     * video.tts: auto / elevenlabs / polly / say / none.
     * auto는 StoryboardCli와 같은 순서 — ElevenLabs 키 → AWS 키(Polly) → macOS say → 무음
     */
    private Narrator narrator(NarrationVoice voice) {
        boolean male = voice == NarrationVoice.MALE;
        boolean hasEleven = !elevenLabsKey.isBlank();
        boolean hasAws = !awsAccessKey.isBlank() && !awsSecretKey.isBlank() && !awsAccessKey.startsWith("local-dummy");
        String mode = !"auto".equals(tts) ? tts
                : hasEleven ? "elevenlabs" : hasAws ? "polly" : Narrator.macSayAvailable() ? "say" : "none";
        return switch (mode) {
            case "elevenlabs" -> {
                if (!hasEleven) throw new IllegalStateException("video.tts=elevenlabs 는 ELEVENLABS_API_KEY 가 필요해요");
                if (male && elevenLabsMaleVoice.isBlank()) log.warn("[영상] ELEVENLABS_MALE_VOICE_ID 가 없어 남자 목소리 대신 기본 목소리로 읽어요");
                yield Narrator.elevenLabs(elevenLabsKey, male && !elevenLabsMaleVoice.isBlank() ? elevenLabsMaleVoice : elevenLabsVoice,
                        elevenLabsModel);
            }
            case "polly" -> {
                if (!hasAws) throw new IllegalStateException("video.tts=polly 는 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 가 필요해요");
                Narrator base = Narrator.polly(awsAccessKey, awsSecretKey, awsRegion, pollyVoice);
                yield male ? Narrator.withFallback(Narrator.polly(awsAccessKey, awsSecretKey, awsRegion, pollyMaleVoice, pollyMaleEngine),
                        base, msg -> log.warn("[영상] {}", msg)) : base;
            }
            case "say" -> Narrator.macSay("Yuna");
            case "none" -> null;
            default -> throw new IllegalStateException("video.tts 는 auto | elevenlabs | polly | say | none 중 하나예요: " + tts);
        };
    }

    public record Result(String id, Path video, Path thumbnail, double durationSec,
                         List<StoryboardRenderer.SceneReport> scenes) {}
}
