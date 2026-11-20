-- 배포 DB(MySQL) 반영용. 운영은 ddl-auto: validate 라 서버 배포 전에 먼저 실행해야 함.
-- 2026-11-17: 가게 리뷰 (별점·본문·공개 여부 + 사진/영상 합쳐 최대 3개).
-- QR 방문 인증(quest_visit)이 있어야 쓰고 같은 인증으로 여러 개 가능. 보상 뽑기 3회는 인증당 첫 리뷰에만 (인증 1건당 한 번). 사장님은 숨길 수 있음

CREATE TABLE review (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    member_id      BIGINT        NOT NULL,
    store_id       BIGINT        NOT NULL,
    visit_id       BIGINT,
    rating         INT           NOT NULL,
    content        VARCHAR(1000) NOT NULL,
    private_review BIT           NOT NULL DEFAULT 0,
    hidden         BIT           NOT NULL DEFAULT 0,
    video_url      VARCHAR(255),
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_review_visit (visit_id),
    KEY idx_review_store (store_id),
    KEY idx_review_member (member_id),
    CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_review_store FOREIGN KEY (store_id) REFERENCES store (id)
);

CREATE TABLE review_photo (
    review_id   BIGINT       NOT NULL,
    photo_order INT          NOT NULL,
    url         VARCHAR(255) NOT NULL,
    PRIMARY KEY (review_id, photo_order),
    CONSTRAINT fk_review_photo_review FOREIGN KEY (review_id) REFERENCES review (id)
);

-- 보상 뽑기권: 방문 인증 1건당 평생 1장. 리뷰를 지우면 remaining = 0, review_id = NULL
CREATE TABLE review_draw_ticket (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    member_id  BIGINT NOT NULL,
    visit_id   BIGINT NOT NULL,
    review_id  BIGINT,
    remaining  INT    NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_draw_ticket_visit (visit_id),
    UNIQUE KEY uk_review_draw_ticket_review (review_id),
    KEY idx_review_draw_ticket_member (member_id)
);

-- 보상 뽑기 기록 (1회 = 1행). reward_type: FEED / COUPON
CREATE TABLE review_draw (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    member_id      BIGINT      NOT NULL,
    ticket_id      BIGINT      NOT NULL,
    reward_type    VARCHAR(10) NOT NULL,
    feed_amount    INT         NOT NULL,
    user_coupon_id BIGINT,
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_review_draw_member (member_id)
);
