package com.commicom.itda.domain.generation.controller;

import com.commicom.itda.domain.generation.storyboard.MenuData;
import com.commicom.itda.domain.generation.storyboard.StoryboardGenerator;
import com.commicom.itda.domain.generation.storyboard.StoryboardGenerator.StoryboardGenerationException;
import com.commicom.itda.domain.generation.storyboard.StoryboardJson;
import com.commicom.itda.domain.generation.storyboard.StoryboardPrompt;
import com.commicom.itda.domain.generation.storyboard.StoryboardValidator;
import com.commicom.itda.domain.generation.storyboard.StoryboardVideoPipeline;
import com.commicom.itda.global.response.ApiResponse;
import com.commicom.itda.infra.video.storyboard.StoryboardVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * local 프로필 전용: 스토리보드 프롬프트 다듬기와 영상까지 한 번에 돌려 보기용.
 * sample은 server/docs/storyboard-samples/ 의 파일 이름 앞부분 (01-donkatsu, 02-cafe, 03-izakaya).
 */
@Tag(name = "Dev", description = "로컬 테스트 전용")
@Profile("local")
@RestController
@RequestMapping("/api/dev/storyboard")
@RequiredArgsConstructor
public class DevStoryboardController {

    private static final Path SAMPLES = Path.of("docs/storyboard-samples");

    private final StoryboardPrompt prompt;
    private final StoryboardValidator validator;
    private final ObjectProvider<StoryboardGenerator> generator;
    private final ObjectProvider<StoryboardVideoPipeline> pipeline;
    private final StoryboardVideoService videoService;

    @Operation(summary = "[로컬] 샘플 메뉴로 만든 프롬프트",
            description = "결과를 claude.ai 등에 그대로 붙여 넣어 응답을 볼 수 있다.")
    @GetMapping(value = "/samples/{sample}/prompt", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String samplePrompt(@PathVariable String sample,
                               @Parameter(description = "사장님 한마디") @RequestParam(required = false) String appeal) {
        return prompt.build(loadMenu(sample), appeal);
    }

    @Operation(summary = "[로컬] LLM 응답을 샘플 메뉴 기준으로 검증",
            description = "본문에 모델 응답을 그대로 붙여 넣는다 (```json 코드블록이 있어도 됨). errors가 비면 통과.")
    @PostMapping(value = "/samples/{sample}/validate", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ApiResponse<Map<String, Object>> validateSample(@PathVariable String sample, @RequestBody String output) {
        return ApiResponse.onSuccess(validate(loadMenu(sample), output));
    }

    @Operation(summary = "[로컬] 샘플 메뉴로 스토리보드 생성 (Bedrock 호출)",
            description = "생성 → 검증 → 최대 2회 재요청까지 실제로 돌린다. 시도별 원문과 오류를 함께 돌려준다.")
    @PostMapping("/samples/{sample}/generate")
    public ApiResponse<Object> generateSample(@PathVariable String sample,
                                              @Parameter(description = "사장님 한마디") @RequestParam(required = false) String appeal) {
        try {
            return ApiResponse.onSuccess(generator.getObject().generate(loadMenu(sample), appeal));
        } catch (StoryboardGenerationException e) {
            return ApiResponse.onSuccess(Map.of("failed", true, "attempts", e.getAttempts()));
        }
    }

    @Operation(summary = "[로컬] 메뉴 데이터 + 사진 → (이미지 생성) → 장면 JSON → mp4 (Bedrock + 영상 렌더링)",
            description = "menu는 메뉴 데이터 JSON (메뉴 추출 결과), photos는 그 photos[] 순서대로 같은 장수. "
                    + "음식 사진이 없어 image_requests가 있으면 Runway로 이미지를 먼저 만든다 (장당 5 credits, 같은 프롬프트는 재사용). "
                    + "이때 photos[]가 비어 있으면 사진은 안 올려도 된다. "
                    + "runway=true면 motion=ai 장면에 Runway 크레딧을 쓴다 (기본은 zoom_in으로 대체). "
                    + "결과의 videoUrl을 열면 영상이 나온다.")
    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Object> video(@RequestParam String menu,
                                     @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
                                     @Parameter(description = "사장님 한마디") @RequestParam(required = false) String appeal,
                                     @RequestParam(defaultValue = "false") boolean runway) {
        MenuData menuData;
        try {
            menuData = StoryboardJson.parseMenu(menu);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "메뉴 데이터 JSON을 읽지 못했어요: " + e.getMessage());
        }
        return makeVideo(menuData, appeal, photos, runway);
    }

    @Operation(summary = "[로컬] 샘플 메뉴 + 사진 → 장면 JSON → mp4 (Bedrock + 영상 렌더링)",
            description = "photos는 샘플 메뉴의 photos[] 순서대로 같은 장수 (01-donkatsu 4장, 02-cafe 3장, 03-izakaya 2장).")
    @PostMapping(value = "/samples/{sample}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Object> sampleVideo(@PathVariable String sample,
                                           @RequestPart("photos") List<MultipartFile> photos,
                                           @Parameter(description = "사장님 한마디") @RequestParam(required = false) String appeal,
                                           @RequestParam(defaultValue = "false") boolean runway) {
        return makeVideo(loadMenu(sample), appeal, photos, runway);
    }

    @Operation(summary = "[로컬] 샘플 장면 JSON + 사진 → mp4 (LLM 호출 없음)",
            description = "docs/storyboard-samples/{sample}.storyboard.json을 그대로 렌더링한다. 영상 쪽만 빨리 확인할 때.")
    @PostMapping(value = "/samples/{sample}/render", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Object> sampleRender(@PathVariable String sample,
                                            @RequestPart("photos") List<MultipartFile> photos,
                                            @RequestParam(defaultValue = "false") boolean runway) {
        String storyboardJson = readSample(sample + ".storyboard.json");
        return withPhotos(photos, paths -> videoResponse(videoService.render(storyboardJson, paths, runway)));
    }

    @Operation(summary = "[로컬] 만든 영상 보기", description = "영상 응답의 id. 브라우저에서 바로 재생된다.")
    @GetMapping("/videos/{id}")
    public ResponseEntity<FileSystemResource> videoFile(@PathVariable String id) {
        if (!id.matches("[0-9a-f-]{36}")) {
            throw new ResponseStatusException(BAD_REQUEST, "영상 id가 올바르지 않아요");
        }
        Path file = videoService.videoFile(id);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(NOT_FOUND, "영상이 없어요: " + id);
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("video/mp4")).body(new FileSystemResource(file));
    }

    private ApiResponse<Object> makeVideo(MenuData menu, String appeal, List<MultipartFile> photos, boolean runway) {
        return withPhotos(photos, paths -> {
            try {
                StoryboardVideoPipeline.Result r = pipeline.getObject().make(menu, appeal, paths, runway);
                Map<String, Object> body = videoResponse(r.video());
                body.put("generatedImages", r.images().generated().stream()
                        .map(g -> Map.of("menuRef", g.menuRef(), "prompt", g.prompt(), "cached", g.cached()))
                        .toList());
                body.put("imageCredits", r.images().creditsSpent());
                body.put("storyboard", r.storyboard());
                body.put("attempts", r.attempts());
                return body;
            } catch (StoryboardGenerationException e) {
                return Map.of("failed", true, "attempts", e.getAttempts());
            }
        });
    }

    private static Map<String, Object> videoResponse(StoryboardVideoService.Result v) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", v.id());
        body.put("videoUrl", "/api/dev/storyboard/videos/" + v.id());
        body.put("videoPath", v.video().toAbsolutePath().toString());
        body.put("durationSec", v.durationSec());
        body.put("scenes", v.scenes());
        return body;
    }

    @FunctionalInterface
    private interface PhotoTask {
        Object run(List<Path> photos) throws Exception;
    }

    /** 올린 사진을 임시 폴더에 순서대로 저장해 넘기고, 끝나면 지운다 */
    private static ApiResponse<Object> withPhotos(List<MultipartFile> files, PhotoTask task) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("storyboard-photos-");
            List<Path> paths = new ArrayList<>();
            for (MultipartFile f : files == null ? List.<MultipartFile>of() : files) {
                if (f.isEmpty()) continue;
                String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
                String ext = name.contains(".")
                        ? name.substring(name.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "")
                        : ".jpg";
                Path p = dir.resolve(String.format("%02d%s", paths.size(), ext));
                f.transferTo(p);
                paths.add(p);
            }
            return ApiResponse.onSuccess(task.run(paths));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("영상을 만들지 못했어요: " + e.getMessage(), e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    private Map<String, Object> validate(MenuData menu, String output) {
        try {
            List<String> errors = validator.validate(StoryboardJson.parseStoryboard(output), menu);
            return Map.of("passed", errors.isEmpty(), "errors", errors);
        } catch (IOException e) {
            return Map.of("passed", false, "errors", List.of("JSON으로 읽을 수 없습니다: " + e.getMessage()));
        }
    }

    private MenuData loadMenu(String sample) {
        try {
            return StoryboardJson.parseMenu(readSample(sample + ".menu.json"));
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "샘플 메뉴를 읽지 못했어요: " + e.getMessage());
        }
    }

    private static String readSample(String fileName) {
        if (!fileName.matches("[A-Za-z0-9_-]+\\.[a-z.]+")) {
            throw new ResponseStatusException(BAD_REQUEST, "샘플 이름이 올바르지 않아요");
        }
        Path file = SAMPLES.resolve(fileName);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(NOT_FOUND, "샘플이 없어요: " + file);
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "샘플을 읽지 못했어요: " + e.getMessage());
        }
    }
}
