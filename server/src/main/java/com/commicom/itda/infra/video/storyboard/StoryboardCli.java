package com.commicom.itda.infra.video.storyboard;

import com.commicom.itda.infra.video.storyboard.StoryboardChanges.Change;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 장면 JSON → mp4 를 서버 없이 바로 돌려 보는 명령.
 *
 * <pre>
 * cd server
 * ./gradlew renderStoryboard --args="--storyboard docs/storyboard-samples/02-cafe.storyboard.json \
 *     --photos test/카페1.png,test/커피1.png,test/카페2.png,test/menu1.png --out build/video/cafe.mp4"
 *
 * 옵션
 *   --storyboard  장면 JSON 파일 (필수)
 *   --photos      사진 파일을 쉼표로, image_index 순서대로. 폴더를 주면 이름순 (필수)
 *   --out         결과 mp4 (기본 build/video/out.mp4) — 같은 이름 .jpg 로 썸네일도 저장
 *   --changes     수정 변경분 JSON 배열 [{scene_id, field, before, after}] — 적용 후 렌더링
 *   --tts         elevenlabs | polly | say | none  (기본: ElevenLabs 키 → AWS 키 → macOS say 순)
 *   --no-runway   키가 있어도 Runway 를 부르지 않음 (motion=ai 도 zoom_in 으로)
 *   --cache       캐시 폴더 (기본 build/video-cache)
 * </pre>
 *
 * 키는 환경변수 또는 server/.env 에서 읽는다: RUNWAYML_API_SECRET, ELEVENLABS_API_KEY (ELEVENLABS_VOICE_ID, ELEVENLABS_MODEL), AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY,
 * AWS_REGION, AWS_POLLY_VOICE_ID, FFMPEG_PATH
 */
public final class StoryboardCli {

    public static void main(String[] argv) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Map<String, String> a = parse(argv);
        if (!a.containsKey("storyboard") || !a.containsKey("photos")) {
            System.err.println("사용법: --storyboard <json> --photos <a.jpg,b.jpg,...|폴더> [--out out.mp4] [--changes changes.json] [--tts polly|say|none] [--no-runway]");
            System.exit(2);
        }
        Map<String, String> env = env();
        ObjectMapper json = new ObjectMapper();

        ObjectNode root = (ObjectNode) json.readTree(Path.of(a.get("storyboard")).toFile());
        if (a.containsKey("changes")) {
            List<Change> changes = json.readValue(Path.of(a.get("changes")).toFile(), new TypeReference<>() {});
            System.out.println("수정 변경분 " + changes.size() + "개 적용");
            StoryboardChanges.apply(root, changes).forEach(w -> System.out.println("  ⚠ " + w));
        }
        Storyboard sb = json.treeToValue(root, Storyboard.class);
        List<Path> photos = photos(a.get("photos"));

        String ffmpeg = env.getOrDefault("FFMPEG_PATH", Files.isExecutable(Path.of("/opt/homebrew/bin/ffmpeg"))
                ? "/opt/homebrew/bin/ffmpeg" : "ffmpeg");
        Narrator narrator = narrator(a.get("tts"), env);
        RunwayClipClient runway = a.containsKey("no-runway") ? null
                : new RunwayClipClient(env.get("RUNWAYML_API_SECRET"), System.out::println);

        System.out.printf("「%s」 장면 %d개 · 사진 %d장 · 음성 %s · Runway %s%n", sb.title(), sb.scenes().size(), photos.size(),
                narrator == null ? "없음" : narrator.id(), runway != null && runway.enabled() ? "사용" : "안 씀(zoom_in 대체)");

        StoryboardRenderer renderer = new StoryboardRenderer(ffmpeg,
                Path.of(a.getOrDefault("cache", "build/video-cache")), narrator, runway, System.out::println);
        long start = System.currentTimeMillis();
        StoryboardRenderer.Result r = renderer.render(sb, photos, Path.of(a.getOrDefault("out", "build/video/out.mp4")));
        System.out.printf("완료 %.1f초 영상 (%.1f초 걸림)%n  영상  %s%n  썸네일 %s%n", r.durationSec(),
                (System.currentTimeMillis() - start) / 1000.0, r.video().toAbsolutePath(), r.thumbnail().toAbsolutePath());
    }

    private static Narrator narrator(String mode, Map<String, String> env) {
        String key = env.get("AWS_ACCESS_KEY_ID");
        boolean hasAws = key != null && !key.isBlank() && !key.startsWith("local-dummy");
        String eleven = env.get("ELEVENLABS_API_KEY");
        if (mode == null) {
            mode = eleven != null ? "elevenlabs" : hasAws ? "polly" : (Narrator.macSayAvailable() ? "say" : "none");
        }
        return switch (mode) {
            case "elevenlabs" -> {
                if (eleven == null) throw new IllegalStateException("--tts elevenlabs 는 ELEVENLABS_API_KEY 가 필요");
                yield Narrator.elevenLabs(eleven,
                        env.getOrDefault("ELEVENLABS_VOICE_ID", "uyVNoMrnUku1dZyVEXwD"),
                        env.getOrDefault("ELEVENLABS_MODEL", "eleven_v3"));
            }
            case "polly" -> {
                if (!hasAws) throw new IllegalStateException("--tts polly 는 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 가 필요");
                yield Narrator.polly(key, env.get("AWS_SECRET_ACCESS_KEY"),
                        env.getOrDefault("AWS_REGION", "ap-northeast-2"), env.getOrDefault("AWS_POLLY_VOICE_ID", "Seoyeon"));
            }
            case "say" -> Narrator.macSay("Yuna");
            case "none" -> null;
            default -> throw new IllegalArgumentException("--tts 는 elevenlabs | polly | say | none");
        };
    }

    private static List<Path> photos(String arg) throws Exception {
        Path p = Path.of(arg);
        if (Files.isDirectory(p)) {
            try (var s = Files.list(p)) {
                return s.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().startsWith(".")).sorted().toList();
            }
        }
        List<Path> list = Arrays.stream(arg.split(",")).map(String::strip).map(Path::of).toList();
        for (Path f : list) if (!Files.isRegularFile(f)) throw new IllegalArgumentException("사진 없음: " + f);
        return list;
    }

    private static Map<String, String> parse(String[] argv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < argv.length; i++) {
            if (!argv[i].startsWith("--")) continue;
            String k = argv[i].substring(2);
            boolean hasValue = i + 1 < argv.length && !argv[i + 1].startsWith("--");
            m.put(k, hasValue ? argv[++i] : "true");
        }
        return m;
    }

    /** 환경변수 우선, 없으면 server/.env (KEY=value) */
    static Map<String, String> env() throws Exception {
        Map<String, String> m = new HashMap<>();
        Path dotenv = Path.of(".env");
        if (Files.isRegularFile(dotenv)) {
            for (String line : Files.readAllLines(dotenv)) {
                line = line.strip();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq < 1) continue;
                String v = line.substring(eq + 1).strip();
                if (!v.isEmpty()) m.put(line.substring(0, eq).strip(), v);
            }
        }
        System.getenv().forEach((k, v) -> { if (!v.isBlank()) m.put(k, v); });
        return m;
    }
}
