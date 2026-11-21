package com.commicom.itda.infra.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    /**
     * 올린 사진의 로컬 사본 위치. 서버가 자기가 올린 사진을 다시 읽을 때(영상 생성) 먼저 여기서 찾는다.
     * 서버 IAM 사용자에 s3:GetObject 가 없고 로컬 CloudFront 주소가 가짜여도 영상을 만들 수 있게.
     */
    @Value("${storage.upload-cache-dir:${video.work-dir:./tmp/video}/upload-cache}")
    private String uploadCacheDir;

    /** MultipartFile(이미지 등)을 S3에 업로드하고 CloudFront URL을 반환한다. */
    public String upload(MultipartFile file, String folder) {
        String ext = getExtension(file.getOriginalFilename());
        String key = folder + "/" + UUID.randomUUID() + ext;
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            throw new RuntimeException("S3 업로드 실패: " + e.getMessage(), e);
        }
        log.info("S3 업로드 완료: {}", key);
        keepLocalCopy(file, key);
        return toUrl(key);
    }

    private void keepLocalCopy(MultipartFile file, String key) {
        try {
            Path dest = Path.of(uploadCacheDir).resolve(key);
            Files.createDirectories(dest.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("올린 파일 로컬 사본 저장 실패 (영상 생성 때 S3에서 다시 받음): {}", e.getMessage());
        }
    }

    /** byte 배열(음성 데이터 등)을 S3에 업로드하고 CloudFront URL을 반환한다. */
    public String uploadBytes(byte[] data, String key, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data)
        );
        log.info("S3 업로드 완료: {}", key);
        return toUrl(key);
    }

    /** File(영상 파일 등)을 S3에 업로드하고 CloudFront URL을 반환한다. */
    public String uploadFile(File file, String key, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file)
        );
        log.info("S3 업로드 완료: {}", key);
        return toUrl(key);
    }

    /**
     * File 업로드 + 다운로드 파일 이름 지정. 주소를 열면 이 이름으로 저장된다.
     * (영상 태그 재생에는 영향 없음 — 앱의 [다운로드] 버튼이 영상 주소를 그대로 열면 됨)
     */
    public String uploadFileAsDownload(File file, String key, String contentType, String downloadName) {
        String encoded = java.net.URLEncoder.encode(downloadName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentDisposition("attachment; filename=\"video.mp4\"; filename*=UTF-8''" + encoded)
                        .build(),
                RequestBody.fromFile(file)
        );
        log.info("S3 업로드 완료: {} ({})", key, downloadName);
        return toUrl(key);
    }

    /** 이 서버가 folder 아래에 올린 파일의 주소인지 (다른 곳 주소를 저장하지 않게) */
    public boolean isUploadedUnder(String url, String folder) {
        return url != null && url.startsWith(toUrl(folder + "/")) && !url.contains("..");
    }

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(20)).build();

    /**
     * 이 서버가 올린 파일을 dest에 저장한다. 로컬 사본 → S3(s3:GetObject 권한 필요) → 공개 주소 순으로 시도.
     */
    public void download(String url, Path dest) throws IOException {
        String prefix = toUrl("");
        if (url == null || !url.startsWith(prefix) || url.contains("..")) {
            throw new IllegalArgumentException("이 서버가 올린 파일이 아니에요: " + url);
        }
        String key = url.substring(prefix.length());

        Path local = Path.of(uploadCacheDir).resolve(key);
        if (Files.isRegularFile(local)) {
            Files.copy(local, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (var in = s3Client.getObject(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(bucket).key(key).build())) {
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (Exception e) {
            log.warn("S3에서 받기 실패, 공개 주소로 다시 시도: {} ({})", key, e.getMessage());
        }
        try {
            var res = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(60)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofFile(dest));
            if (res.statusCode() / 100 != 2) {
                Files.deleteIfExists(dest);
                throw new IOException("파일을 받지 못했어요 (" + res.statusCode() + "): " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("파일 받기가 중단됐어요: " + url, e);
        }
    }

    private String toUrl(String key) {
        return "https://" + cloudFrontDomain + "/" + key;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
