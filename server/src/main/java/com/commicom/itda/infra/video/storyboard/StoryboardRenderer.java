package com.commicom.itda.infra.video.storyboard;

import com.commicom.itda.domain.generation.storyboard.SpokenPrice;
import com.commicom.itda.infra.video.storyboard.Storyboard.Scene;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * B 파트: 장면 JSON + 사진 → 세로 mp4 (1080x1920, 30fps).
 *
 * <pre>
 * 장면마다  ① 배경 클립  사진마다 ai 장면의 video_prompt로 Runway 5초 클립을 한 번 만들고 (사진들 것을 동시에 요청),
 *                        그 사진을 쓰는 장면은 모두 그 클립을 이어서 씀 (앞뒤로 왕복 재생해 길이를 늘림)
 *                        → 저해상도 사진을 그대로 키운 정지 화면이 영상에 섞이지 않음
 *                        Runway가 없거나 그 사진에 ai 장면이 없으면 FFmpeg 줌 (zoom_in·zoom_out·static)
 *          ② 내레이션    Polly 또는 macOS say → 장면 길이에 맞춤 (길면 살짝 빠르게)
 *          ③ 장면 조각   배경과 내레이션 합성
 * 전체      조각 이어붙이기 → 고정 제목과 내레이션 구간별 자막 → BGM → 최종 mp4 + 썸네일
 * </pre>
 *
 * 모든 중간 결과는 입력 내용의 해시로 캐시에 저장한다. 그래서 수정 요청 후 다시 돌리면
 * 바뀐 장면만 새로 만들고 나머지는 그대로 쓴다 (스키마 5절의 "변경 장면만 재생성").
 */
public final class StoryboardRenderer {

    /** 렌더링 방식이 바뀌면 올려서 기존 캐시를 무효화 */
    private static final String VERSION = "r9";
    private static final String CAPTION_VERSION = "2";
    private static final int FPS = 30;
    /** 첫 장면 내레이션 앞 여백. 나머지 장면은 앞 장면 말에 바로 이어지도록 거의 붙인다 */
    private static final double VOICE_LEAD_IN = 0.2;
    private static final double VOICE_LEAD_IN_NEXT = 0.05;
    /** 장면 끝에 남겨 두는 여백 */
    private static final double VOICE_TAIL = 0.05;
    private static final double MAX_TEMPO = 1.5;

    public enum State { NEW, CACHED, FALLBACK, NONE }

    public record SceneReport(int id, String motion, State clip, State voice, State segment, String note) {}

    public record Result(Path video, Path thumbnail, double durationSec, List<SceneReport> scenes) {}

    private record CaptionCue(Path image, double start, double end) {}

    private final Ffmpeg ff;
    private final Path cacheDir;
    private final Narrator narrator;          // null이면 무음
    private final RunwayClipClient runway;     // 키 없으면 비활성
    private final CaptionRenderer captions = new CaptionRenderer();
    private final Consumer<String> log;

    StoryboardRenderer(String ffmpegPath, Path cacheDir, Narrator narrator, RunwayClipClient runway, Consumer<String> log)
            throws IOException {
        this.ff = new Ffmpeg(ffmpegPath);
        this.cacheDir = Files.createDirectories(cacheDir);
        this.narrator = narrator;
        this.runway = runway;
        this.log = log;
    }

    /**
     * @param photos photoUrls 순서와 같은 로컬 사진 파일 (image_index 가 이 목록의 순번)
     */
    public Result render(Storyboard sb, List<Path> photos, Path out) throws Exception {
        return render(sb, photos, out, null);
    }

    Result render(Storyboard sb, List<Path> photos, Path out, VideoTemplate templateOverride) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        List<Path> segments = new ArrayList<>();
        List<CaptionCue> cues = new ArrayList<>();

        List<Scene> ordered = sb.orderedScenes();
        List<Double> durations = new ArrayList<>();
        // 전환 시간만큼 앞 장면을 길게 만들어 겹치므로 전체 길이는 그대로
        ThemeStyle theme = ThemeStyle.of(sb.theme());
        log.accept("  주제 " + (sb.theme() == null ? "없음" : sb.theme()) + " → 전환 " + theme.transition + " " + theme.transitionSec + "초");
        VideoTemplate template = templateOverride == null ? sb.template() : templateOverride;
        String templateSource = templateOverride != null ? "서버 설정으로 고정"
                : sb.meta() != null && sb.meta().hasNonNull("template") ? "지정" : "기본";
        log.accept("  템플릿 " + template.id() + " (" + templateSource + ")");
        // 사진 칸이 화면 일부인 템플릿: 클립 아래·위에 깔 바탕
        Backdrop backdrop = backdrop(template);
        int clipW = template.photoBox == null ? 1080 : template.photoBox.w();
        int clipH = template.photoBox == null ? 1920 : template.photoBox.h();

        // 사진 번호 → 그 사진의 Runway 프롬프트 (그 사진을 쓰는 첫 ai 장면 것). 이 사진을 쓰는 장면은 모두 같은 클립을 이어 씀
        boolean runwayOn = runway != null && runway.enabled();
        Map<Integer, String> clipPrompts = new HashMap<>();
        if (runwayOn) {
            for (Scene s : ordered) {
                if ("ai".equals(s.motion()) && s.videoPrompt() != null && !s.videoPrompt().isBlank()) {
                    clipPrompts.putIfAbsent(s.imageIndex(), s.videoPrompt());
                }
            }
        }
        // 사진별 Runway 클립을 먼저 한꺼번에 요청해 둔다 (하나씩 기다리면 사진 수만큼 시간이 늘어남)
        Map<Integer, State> rawStates = generateClips(clipPrompts, photos);
        /** 사진 번호 → 그 사진 클립에서 이미 쓴 시간(초) */
        Map<Integer, Double> clipUsed = new HashMap<>();
        double sceneStart = 0;
        for (int idx = 0; idx < ordered.size(); idx++) {
            Scene scene = ordered.get(idx);
            boolean lastScene = idx == ordered.size() - 1;
            double length = scene.duration() + (lastScene ? 0 : theme.transitionSec);
            if (scene.imageIndex() < 0 || scene.imageIndex() >= photos.size()) {
                throw new IllegalArgumentException("장면 " + scene.id() + ": image_index " + scene.imageIndex()
                        + " 에 해당하는 사진이 없음 (사진 " + photos.size() + "장)");
            }
            Path photo = photos.get(scene.imageIndex());
            String photoHash = fileHash(photo);
            String note = "";

            // ① 배경 클립
            String motion = scene.motion() == null ? "static" : scene.motion();
            Path clip;
            String clipKey;
            State clipState;
            String clipPrompt = clipPrompts.get(scene.imageIndex());
            if (clipPrompt != null) {
                String runwayKey = hash("runway", photoHash, clipPrompt);
                Path raw = cacheDir.resolve("clip-" + runwayKey + ".mp4");
                // generateClips가 이미 만들어 둠. 새로 만든 클립은 그 사진의 첫 장면에서만 "새로"로 표시
                State rawState = rawStates.getOrDefault(scene.imageIndex(), State.CACHED);
                rawStates.put(scene.imageIndex(), State.CACHED);
                Path loop = cacheDir.resolve("loop-" + runwayKey + ".mp4");
                cached(loop, tmp -> pingPong(raw, tmp));
                double start = clipUsed.getOrDefault(scene.imageIndex(), 0.0);
                clipUsed.put(scene.imageIndex(), start + scene.duration());
                clipKey = hash("runway-part", runwayKey, fmt(start), fmt(length));
                clip = cacheDir.resolve("clip-" + clipKey + ".mp4");
                Path loopF = loop;
                cached(clip, tmp -> subClip(loopF, start, length, tmp));
                if ("ai".equals(motion)) {
                    clipState = rawState;
                } else {
                    clipState = State.CACHED;
                    note = String.format(Locale.ROOT, "%d번 사진 Runway 클립 %.1f초부터 이어 씀", scene.imageIndex(), start);
                }
            } else {
                String zoom = "ai".equals(motion) ? "zoom_in" : motion;
                if ("ai".equals(motion)) note = runway == null ? "Runway 끔 → zoom_in으로 대체" : "Runway 키 없음 → zoom_in으로 대체";
                clipKey = hash("zoom", photoHash, zoom, fmt(length), clipW + "x" + clipH);
                clip = cacheDir.resolve("clip-" + clipKey + ".mp4");
                clipState = cached(clip, tmp -> zoomClip(photo, zoom, length, clipW, clipH, tmp));
                if ("ai".equals(motion)) clipState = State.FALLBACK;
            }

            // ② 내레이션
            Path voice = null;
            String voiceKey = "none";
            State voiceState = State.NONE;
            double leadIn = idx == 0 ? VOICE_LEAD_IN : VOICE_LEAD_IN_NEXT;
            double speechDuration = 0;
            if (scene.hasNarration() && narrator != null) {
                // "12,000원" → "만 이천 원" (TTS마다 숫자 읽는 방식이 달라 미리 풀어 둔다)
                String spoken = SpokenPrice.speak(scene.narration().strip());
                voiceKey = hash(narrator.id(), spoken);
                voice = cacheDir.resolve("voice-" + voiceKey + ".wav");
                Path v = voice;
                voiceState = cached(v, tmp -> narrator.synthesize(spoken, tmp, ff));
                speechDuration = ff.duration(voice);
                double available = Math.max(1.0 / FPS, scene.duration() - leadIn - VOICE_TAIL);
                if (speechDuration > available) {
                    double tempo = Math.min(MAX_TEMPO, speechDuration / available);
                    String adjustment = String.format(Locale.ROOT, "내레이션 %.1f초 > 장면 → %.2f배속%s",
                            speechDuration, tempo, speechDuration / available > MAX_TEMPO ? " (그래도 길어 잘림)" : "");
                    note = note.isEmpty() ? adjustment : note + ", " + adjustment;
                    voiceKey = hash("voice-fit", VERSION, voiceKey, fmt(tempo), fmt(available));
                    voice = cacheDir.resolve("voice-" + voiceKey + ".wav");
                    cached(voice, tmp -> ff.run(List.of("-i", v.toString(), "-af",
                            "atempo=" + fmt(tempo) + ",atrim=duration=" + fmt(available),
                            "-c:a", "pcm_s16le", tmp.toString())));
                    // atempo가 적용된 파일의 실제 길이를 자막 종료 시각에 사용한다.
                    speechDuration = ff.duration(voice);
                }
            }

            // 음성을 쓴 장면은 마지막 장면까지 실제 대사를 자막으로 쓴다.
            Storyboard.Caption caption = scene.caption();
            if (voice != null) {
                caption = new Storyboard.Caption(scene.narration(), caption == null ? null : caption.position(),
                        caption == null ? null : caption.emphasis());
            }
            if (caption != null && caption.text() != null && !caption.text().isBlank()) {
                double start = voice == null ? 0 : leadIn;
                double end = voice == null ? scene.duration() : Math.min(scene.duration(), start + speechDuration);
                List<String> lines = captions.captionLines(caption.text(), template);
                int totalLength = lines.stream().mapToInt(String::length).sum();
                int shownLength = 0;
                double cueStart = sceneStart + start;
                for (String text : lines) {
                    Storyboard.Caption line = new Storyboard.Caption(text, caption.position(), caption.emphasis());
                    String capKey = hash("cap", CAPTION_VERSION, line.toString(), String.valueOf(sb.style()), template.id());
                    Path cap = cacheDir.resolve("cap-" + capKey + ".png");
                    cached(cap, tmp -> captions.renderCaption(line, sb.style(), template, tmp));
                    shownLength += text.length();
                    // 긴 자막은 같은 내레이션 구간 안에서 줄 길이 비율로 나눠 표시한다.
                    double cueEnd = sceneStart + start + (end - start) * shownLength / totalLength;
                    cues.add(new CaptionCue(cap, cueStart, cueEnd));
                    cueStart = cueEnd;
                }
            }
            sceneStart += scene.duration();

            // ③ 장면 조각
            String segKey = hash(VERSION, clipKey, voiceKey, fmt(length), fmt(leadIn), template.id());
            Path seg = cacheDir.resolve("seg-" + segKey + ".mp4");
            Path clipF = clip;
            Path voiceF = voice;
            State segState = cached(seg, tmp -> segment(clipF, voiceF, length, leadIn, backdrop, tmp));

            reports.add(new SceneReport(scene.id(), motion, clipState, voiceState, segState, note));
            log.accept(String.format("  장면 %d (%s, %.1f초)  클립 %s · 음성 %s · 합성 %s %s",
                    scene.id(), motion, scene.duration(), label(clipState), label(voiceState), label(segState), note));
            segments.add(seg);
            durations.add(scene.duration());
        }

        // 전체: 겹쳐 넘기며 이어붙이기 → BGM → 최종
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path body = cacheDir.resolve("body-" + System.nanoTime() + ".mp4");
        joinWithTransitions(segments, durations, theme, body);
        Path header = null;
        if (template.hasHeader() && sb.title() != null && !sb.title().isBlank()) {
            header = cacheDir.resolve("header-" + hash(VERSION, template.id(), sb.title(), sb.storeName()) + ".png");
            cached(header, tmp -> captions.renderHeader(template, sb.title(), sb.storeName(), tmp));
        }
        if (header != null || !cues.isEmpty()) {
            Path withGraphics = cacheDir.resolve("body-" + System.nanoTime() + ".mp4");
            overlayGraphics(body, header, cues, withGraphics);
            Files.deleteIfExists(body);
            body = withGraphics;
        }
        double total = ff.duration(body);

        Path bgm = bgmFile(sb.bgm() == null ? null : sb.bgm().mood());
        if (bgm != null) {
            String t = fmt(total);
            String fadeOut = fmt(Math.max(0, total - 1.2));
            ff.run(List.of("-i", body.toString(), "-stream_loop", "-1", "-i", bgm.toString(),
                    "-filter_complex",
                    "[0:a]asplit=2[voice][key];"
                            + "[1:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=0.35,atrim=duration=" + t
                            + ",afade=t=in:d=0.6,afade=t=out:st=" + fadeOut + ":d=1.2[music];"
                            + "[music][key]sidechaincompress=threshold=0.02:ratio=6:attack=15:release=350[ducked];"
                            + "[voice][ducked]amix=inputs=2:duration=first:normalize=0[a]",
                    "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "160k",
                    "-movflags", "+faststart", out.toString()));
        } else {
            log.accept("  BGM 파일 없음 (resources/bgm/" + (sb.bgm() == null ? "?" : sb.bgm().mood()) + ".mp3) → 내레이션만");
            ff.run(List.of("-i", body.toString(), "-c", "copy", "-movflags", "+faststart", out.toString()));
        }
        Files.deleteIfExists(body);

        Path thumb = out.resolveSibling(stripExt(out.getFileName().toString()) + ".jpg");
        ff.run(List.of("-ss", "1", "-i", out.toString(), "-frames:v", "1", "-q:v", "2", thumb.toString()));
        return new Result(out, thumb, total, reports);
    }

    /**
     * 사진마다 Runway 클립을 동시에 요청하고 모두 끝날 때까지 기다린다.
     * 사진 3장이면 예전(하나씩 차례로)보다 기다리는 시간이 거의 1/3. 크레딧은 같다.
     * 하나가 실패해도 나머지는 끝까지 기다린 뒤 예외를 던진다 — 성공한 클립은 캐시에 남아 다시 돌릴 때 크레딧이 들지 않는다.
     * Runway 동시 실행 한도를 넘으면 Runway 쪽에서 대기(THROTTLED)시켰다가 차례로 처리한다.
     *
     * @return 사진 번호 → 클립 상태 (NEW: 이번에 만듦, CACHED: 캐시 재사용)
     */
    private Map<Integer, State> generateClips(Map<Integer, String> clipPrompts, List<Path> photos) throws Exception {
        Map<Integer, State> states = new HashMap<>();
        // 같은 사진·같은 프롬프트는 결과 파일이 같으므로 한 번만 요청
        Map<Path, Future<State>> byFile = new LinkedHashMap<>();
        Map<Integer, Path> fileOf = new LinkedHashMap<>();
        ExecutorService pool = Executors.newCachedThreadPool();
        int fresh = 0;
        try {
            for (Map.Entry<Integer, String> e : clipPrompts.entrySet()) {
                int i = e.getKey();
                if (i < 0 || i >= photos.size()) continue;   // 장면 반복문에서 알아보기 쉬운 오류로 막힘
                Path photo = photos.get(i);
                String prompt = e.getValue();
                Path raw = cacheDir.resolve("clip-" + hash("runway", fileHash(photo), prompt) + ".mp4");
                fileOf.put(i, raw);
                if (byFile.containsKey(raw)) continue;
                if (!Files.exists(raw)) fresh++;
                byFile.put(raw, pool.submit(() -> cached(raw, tmp -> runway.generate(photo, prompt, tmp, ff))));
            }
            if (fresh > 0) log.accept("  Runway 클립 " + fresh + "개 동시 요청 (캐시 " + (byFile.size() - fresh) + "개)");

            Exception failure = null;
            Map<Path, State> done = new HashMap<>();
            for (Map.Entry<Path, Future<State>> job : byFile.entrySet()) {
                try {
                    done.put(job.getKey(), job.getValue().get());
                } catch (ExecutionException ex) {
                    Exception cause = ex.getCause() instanceof Exception c ? c : ex;
                    if (failure == null) failure = cause;
                    else failure.addSuppressed(cause);
                }
            }
            if (failure != null) throw failure;
            // 같은 파일을 쓰는 사진이 둘이면 "새로"는 앞 사진에만
            Map<Path, Boolean> reported = new HashMap<>();
            fileOf.forEach((i, f) -> states.put(i, reported.put(f, true) == null ? done.get(f) : State.CACHED));
        } finally {
            pool.shutdownNow();   // 정상 종료면 이미 다 끝남. 생성 취소(인터럽트)면 기다리던 요청도 멈춤
        }
        return states;
    }

    // ── 단계별 FFmpeg ────────────────────────────────────────────────

    /** 사진 칸 템플릿의 바탕 그림 (under: 클립 아래, over: 클립 위 — 없으면 null) */
    private record Backdrop(VideoTemplate.Box box, Path under, Path over) {}

    private Backdrop backdrop(VideoTemplate template) throws Exception {
        if (template.photoBox == null) return null;
        String key = hash("backdrop", VERSION, template.id());
        Path under = cacheDir.resolve("backdrop-" + key + "-under.png");
        Path over = cacheDir.resolve("backdrop-" + key + "-over.png");
        if (!Files.exists(under)) captions.renderBackdrop(template, under, over);
        return new Backdrop(template.photoBox, under, Files.exists(over) ? over : null);
    }

    /** 전환을 합성한 뒤에 얹어 장면 경계에서 자막이 겹치거나 밀려가지 않게 한다. */
    private void overlayGraphics(Path video, Path header, List<CaptionCue> cues, Path out) throws Exception {
        List<String> args = new ArrayList<>(List.of("-i", video.toString()));
        StringBuilder filters = new StringBuilder();
        String current = "[0:v]";
        if (header != null) {
            args.addAll(List.of("-loop", "1", "-framerate", String.valueOf(FPS), "-i", header.toString()));
            filters.append("[1:v]format=rgba,fade=t=in:st=0:d=0.4:alpha=1[header];")
                    .append(current).append("[header]overlay=shortest=1[titled];");
            current = "[titled]";
        }
        for (int i = 0; i < cues.size(); i++) {
            CaptionCue cue = cues.get(i);
            int input = (header == null ? 1 : 2) + i;
            args.addAll(List.of("-i", cue.image().toString()));
            String next = "[captioned" + i + "]";
            filters.append(current).append("[").append(input).append(":v]overlay=enable='gte(t,")
                    .append(fmt(cue.start())).append(")*lt(t,").append(fmt(cue.end())).append(")'").append(next).append(";");
            current = next;
        }
        filters.append(current).append("format=yuv420p[v]");
        args.addAll(List.of("-filter_complex", filters.toString(), "-map", "[v]", "-map", "0:a",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-c:a", "copy", out.toString()));
        ff.run(args);
    }

    /** Runway 클립 → 정방향 + 역방향을 이은 클립 (끝에서 멈추지 않고 되감기듯 계속 움직임) */
    private void pingPong(Path clip, Path out) throws Exception {
        ff.run(List.of("-i", clip.toString(),
                "-filter_complex", "[0:v]split[f][b];[b]reverse[r];[f][r]concat=n=2:v=1:a=0[v]",
                "-map", "[v]", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", out.toString()));
    }

    /** 왕복 클립의 start초부터 length초 (넘치면 처음부터 다시 돌아 이어짐) */
    private void subClip(Path loop, double start, double length, Path out) throws Exception {
        double total = ff.duration(loop);
        double from = total > 0 ? start % total : 0;
        ff.run(List.of("-stream_loop", "-1", "-i", loop.toString(),
                "-ss", fmt(from), "-t", fmt(length),
                "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", out.toString()));
    }

    /** 사진 → w×h, duration 초 길이 줌 클립 (2배로 키운 뒤 줌해서 떨림을 줄임) */
    private void zoomClip(Path photo, String motion, double duration, int w, int h, Path out) throws Exception {
        int frames = (int) Math.round(duration * FPS);
        String z = switch (motion) {
            case "zoom_in" -> "1+0.12*on/" + frames;
            case "zoom_out" -> "1.12-0.12*on/" + frames;
            default -> "1";
        };
        ff.run(List.of("-i", photo.toString(),
                "-filter_complex",
                "[0:v]scale=" + w * 2 + ":" + h * 2 + ":force_original_aspect_ratio=increase:flags=lanczos,crop=" + w * 2 + ":" + h * 2 + ","
                        + "zoompan=z='" + z + "':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=" + frames
                        + ":s=" + w + "x" + h + ":fps=" + FPS + ",format=yuv420p[v]",
                "-map", "[v]", "-frames:v", String.valueOf(frames),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", out.toString()));
    }

    /**
     * 장면 조각들을 주제의 전환 효과로 겹쳐 이어붙인다 (소리는 장면 시작 시점에 그대로 섞음).
     * 앞 장면들은 전환 시간만큼 길게 만들어 두었으므로 k번째 장면은 정확히 앞 장면 duration 합 시점에 시작한다.
     */
    private void joinWithTransitions(List<Path> segments, List<Double> durations, ThemeStyle theme, Path out) throws Exception {
        if (segments.size() == 1) {
            ff.run(List.of("-i", segments.get(0).toString(), "-c", "copy", out.toString()));
            return;
        }
        List<String> args = new ArrayList<>();
        for (Path s : segments) args.addAll(List.of("-i", s.toString()));
        StringBuilder f = new StringBuilder();
        String v = "[0:v]";
        double offset = 0;
        for (int k = 1; k < segments.size(); k++) {
            offset += durations.get(k - 1);
            String nv = "[v" + k + "]";
            f.append(v).append("[").append(k).append(":v]xfade=transition=").append(theme.transition)
                    .append(":duration=").append(fmt(theme.transitionSec))
                    .append(":offset=").append(fmt(offset)).append(nv).append(";");
            f.append("[").append(k).append(":a]adelay=delays=").append(Math.round(offset * 1000)).append(":all=1[a").append(k).append("];");
            v = nv;
        }
        // 소리는 크로스페이드하지 않고 각 장면 시작 시점에 그대로 놓는다.
        // 크로스페이드하면 다음 장면 내레이션 첫 음절이 작게 들려 말이 끊긴 것처럼 들린다 (장면 오디오 꼬리는 무음).
        f.append("[0:a]");
        for (int k = 1; k < segments.size(); k++) f.append("[a").append(k).append("]");
        f.append("amix=inputs=").append(segments.size()).append(":duration=longest:normalize=0[aout]");
        args.addAll(List.of("-filter_complex", f.toString(), "-map", v, "-map", "[aout]",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", "-r", String.valueOf(FPS),
                "-c:a", "aac", "-b:a", "160k", "-ar", "44100", out.toString()));
        ff.run(args);
    }

    /**
     * 클립과 내레이션을 전환용 꼬리를 포함한 length 길이로 합성한다.
     */
    private void segment(Path clip, Path voice, double length, double leadIn, Backdrop backdrop, Path out) throws Exception {
        String d = fmt(length);
        List<String> args = new ArrayList<>(List.of("-i", clip.toString()));
        int next = 1;
        String bgChain;
        if (backdrop == null) {
            bgChain = "[0:v]scale=1080:1920:force_original_aspect_ratio=increase:flags=lanczos,crop=1080:1920,setsar=1,fps=" + FPS
                    + ",tpad=stop_mode=clone:stop_duration=6[bg];";
        } else {
            // 바탕(under) 위 사진 칸에 클립, 그 위에 모서리 덮개(over)
            VideoTemplate.Box b = backdrop.box();
            int under = next++;
            args.addAll(List.of("-loop", "1", "-framerate", String.valueOf(FPS), "-t", d, "-i", backdrop.under().toString()));
            bgChain = "[0:v]scale=" + b.w() + ":" + b.h() + ":force_original_aspect_ratio=increase:flags=lanczos,crop=" + b.w() + ":" + b.h()
                    + ",setsar=1,fps=" + FPS + ",tpad=stop_mode=clone:stop_duration=6[clip];"
                    + "[" + under + ":v]format=rgba[under];"
                    + "[under][clip]overlay=" + b.x() + ":" + b.y() + (backdrop.over() == null ? "[bg];" : "[b0];");
            if (backdrop.over() != null) {
                int over = next++;
                args.addAll(List.of("-loop", "1", "-framerate", String.valueOf(FPS), "-t", d, "-i", backdrop.over().toString()));
                bgChain += "[" + over + ":v]format=rgba[over];[b0][over]overlay=0:0[bg];";
            }
        }
        int audio = next;
        if (voice != null) {
            args.addAll(List.of("-i", voice.toString()));
        } else {
            args.addAll(List.of("-f", "lavfi", "-t", d, "-i", "anullsrc=r=44100:cl=stereo"));
        }
        args.addAll(List.of(
                "-filter_complex",
                bgChain
                        + "[bg]trim=duration=" + d + ",setpts=PTS-STARTPTS,format=yuv420p[v];"
                        + "[" + audio + ":a]aformat=sample_rates=44100:channel_layouts=stereo,"
                        + "adelay=delays=" + Math.round(leadIn * 1000) + ":all=1,apad,atrim=duration=" + d
                        + ",asetpts=PTS-STARTPTS[a]",
                "-map", "[v]", "-map", "[a]", "-t", d,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-r", String.valueOf(FPS),
                "-c:a", "aac", "-b:a", "160k", "-ar", "44100", out.toString()));
        ff.run(args);
    }

    /** resources/bgm/{mood}.mp3 → 캐시 폴더로 복사 (FFmpeg는 파일 경로가 필요) */
    private Path bgmFile(String mood) throws IOException {
        if (mood == null) return null;
        try (InputStream in = getClass().getResourceAsStream("/bgm/" + mood + ".mp3")) {
            if (in == null) return null;
            Path dest = cacheDir.resolve("bgm-" + mood + ".mp3");
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }
    }

    // ── 캐시 ─────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Producer {
        void make(Path tmp) throws Exception;
    }

    /** target 이 캐시에 있으면 그대로, 없으면 임시 파일로 만든 뒤 옮김 (실패 시 반쯤 만든 파일이 남지 않게) */
    private State cached(Path target, Producer producer) throws Exception {
        if (Files.exists(target)) return State.CACHED;
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path tmp = Files.createTempFile(target.getParent(), name.substring(0, dot) + "-", name.substring(dot));
        try {
            producer.make(tmp);
            try {
                Files.move(tmp, target);
            } catch (FileAlreadyExistsException e) {
                return State.CACHED;
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return State.NEW;
    }

    private static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update(String.valueOf(p).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fileHash(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(Files.readAllBytes(file))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String label(State s) {
        return switch (s) {
            case NEW -> "새로 만듦";
            case CACHED -> "재사용";
            case FALLBACK -> "대체";
            case NONE -> "없음";
        };
    }
}
