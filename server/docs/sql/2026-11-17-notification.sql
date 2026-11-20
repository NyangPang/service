-- 배포 DB(MySQL) 반영용. 운영은 ddl-auto: validate 라 서버 배포 전에 먼저 실행해야 함.
-- 2026-11-17: 앱 안 알림 (사장님: 우리 가게 새 리뷰). 2026-11-17-review.sql 다음에 실행

CREATE TABLE notification (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT       NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(100) NOT NULL,
    message      VARCHAR(200) NOT NULL,
    store_id     BIGINT,
    review_id    BIGINT,
    is_read      BIT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_notification_recipient_read (recipient_id, is_read)
);
