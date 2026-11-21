package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 메뉴 데이터의 image_requests → Runway 이미지 생성을 서버 없이 돌려 보는 명령.
 *
 * <pre>
 * cd server
 * ./gradlew generateMenuImages --args="--menu docs/storyboard-samples/04-dessert.menu.json --photos test/카페1.png --out build/menu-images/dessert"
 *
 *   --menu    메뉴 데이터 JSON (필수)
 *   --photos  photos[] 와 같은 순서의 사진 파일, 쉼표로 (photos[]가 비었으면 생략)
 *   --out     결과 폴더 (기본 build/menu-images) — menu.json + photo-0.png … 를 쓴다
 *   --hd      1080x1920 으로 (8 credits/장, 기본 720x1280 5 credits/장)
 * </pre>
 * 결과 폴더의 사진을 순서대로 renderStoryboard 의 --photos 에 넣으면 된다.
 */
public final class MenuImageCli {

    public static void main(String[] argv) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i < argv.length; i++) {
            if (!argv[i].startsWith("--")) continue;
            boolean hasValue = i + 1 < argv.length && !argv[i + 1].startsWith("--");
            a.put(argv[i].substring(2), hasValue ? argv[++i] : "true");
        }
        if (!a.containsKey("menu")) {
            System.err.println("사용법: --menu <menu.json> [--photos a.jpg,b.jpg] [--out 폴더] [--hd]");
            System.exit(2);
        }
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode menu = (ObjectNode) json.readTree(Path.of(a.get("menu")).toFile());
        List<Path> photos = a.containsKey("photos")
                ? Arrays.stream(a.get("photos").split(",")).map(String::strip).map(Path::of).toList()
                : List.of();
        for (Path p : photos) if (!Files.isRegularFile(p)) throw new IllegalArgumentException("사진 없음: " + p);

        String key = StoryboardCli.env().get("RUNWAYML_API_SECRET");
        int requests = menu.path("image_requests").size();
        System.out.printf("「%s」 업로드 사진 %d장 · 이미지 요청 %d개%n", menu.path("store_name").asText(), photos.size(), requests);

        MenuImageGenerator generator = new MenuImageGenerator(new RunwayImageClient(key, System.out::println),
                Path.of(a.getOrDefault("cache", "build/video-cache")), a.containsKey("hd"), System.out::println);
        long start = System.currentTimeMillis();
        MenuImageGenerator.Result r = generator.generate(menu, photos);

        Path out = Files.createDirectories(Path.of(a.getOrDefault("out", "build/menu-images")));
        json.writeValue(out.resolve("menu.json").toFile(), r.menu());
        List<String> copied = new ArrayList<>();
        for (int i = 0; i < r.photos().size(); i++) {
            Path src = r.photos().get(i);
            String name = src.getFileName().toString();
            Path dest = out.resolve("photo-" + i + name.substring(name.lastIndexOf('.')));
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied.add(dest.toString());
        }
        System.out.printf("완료 (%.1f초, 새로 쓴 크레딧 %d)%n  메뉴 데이터 %s%n  --photos %s%n",
                (System.currentTimeMillis() - start) / 1000.0, r.creditsSpent(),
                out.resolve("menu.json").toAbsolutePath(), String.join(",", copied));
    }
}
