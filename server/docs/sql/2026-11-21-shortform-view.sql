-- 배포 DB(MySQL) 반영용. 운영은 ddl-auto: validate 라 서버 배포 전에 먼저 실행해야 함 (안 하면 서버가 안 켜짐).
-- 2026-11-21: 게시물 조회 기록 (사장님 성과지표의 조회수·조회 후 방문율).
-- 피드에서 2초 이상 본 게시물마다 한 줄. 로그인 손님은 같은 게시물을 하루 1번, 비로그인은 member_id 없이.
-- 게시물이 지워져도 가게 조회수에 남도록 store_id를 같이 저장하고 FK는 걸지 않음 (quest_visit과 같은 방식). 탈퇴하면 member_id만 NULL로.

CREATE TABLE shortform_view (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    shortform_id BIGINT      NOT NULL,
    store_id     BIGINT      NOT NULL,
    member_id    BIGINT,
    view_date    DATE        NOT NULL,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_shortform_view_store_date (store_id, view_date),
    KEY idx_shortform_view_dedupe (shortform_id, member_id, view_date)
);
