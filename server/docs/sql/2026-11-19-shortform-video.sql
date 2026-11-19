-- 배포 DB(MySQL) 반영용. 운영은 ddl-auto: validate 라 서버 배포 전에 먼저 실행해야 함.
-- (지금 운영 EC2 는 SPRING_JPA_HIBERNATE_DDL_AUTO=update 라 배포 때 자동으로 추가됨 — 이미 있으면 실행하지 말 것)
-- 2026-11-19: 영상 게시물 (있으면 피드에서 사진 대신 영상 재생, image_url 은 포스터)

ALTER TABLE shortform ADD COLUMN video_url VARCHAR(1000) NULL;
