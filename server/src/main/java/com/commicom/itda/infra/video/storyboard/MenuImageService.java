package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 서버에서 이미지 생성 단계를 부르는 입구.
 * 메뉴 추출(A) 결과 JSON + 사장님 사진 → 생성 이미지가 붙은 메뉴 JSON + 사진 목록.
 * 결과의 photos 를 사장님 확인 화면에 보여 주고, 확인이 끝나면 그대로 장면 JSON 생성·영상에 넘긴다.
 */
@Slf4j
@Service
public class MenuImageService {

    private final ObjectMapper json = new ObjectMapper();
    private final String runwayKey;
    private final Path cacheDir;
    private final boolean hd;

    public MenuImageService(@Value("${runway.api-key:}") String runwayKey,
                            @Value("${video.work-dir}") String workDir,
                            @Value("${video.image-hd:false}") boolean hd) {
        this.runwayKey = runwayKey;
        this.cacheDir = Path.of(workDir).resolve("image-cache");
        this.hd = hd;
    }

    public record Result(String menuJson, List<Path> photos, List<MenuImageGenerator.Generated> generated, int creditsSpent) {}

    public Result generate(String menuJson, List<Path> uploadedPhotos) throws Exception {
        ObjectNode menu = (ObjectNode) json.readTree(menuJson);
        MenuImageGenerator generator = new MenuImageGenerator(new RunwayImageClient(runwayKey, log::info), cacheDir, hd, log::info);
        MenuImageGenerator.Result r = generator.generate(menu, uploadedPhotos);
        if (!r.generated().isEmpty()) {
            log.info("[이미지 생성] {}장 (새로 {} credits)", r.generated().size(), r.creditsSpent());
        }
        return new Result(json.writeValueAsString(r.menu()), r.photos(), r.generated(), r.creditsSpent());
    }
}
