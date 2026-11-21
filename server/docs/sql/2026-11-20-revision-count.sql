-- 게시물 수정 횟수 제한 (무료 3번, PRO 무제한)
-- shortform.revision_count: 처음 게시물에서 고쳐 만든 횟수 (0 = 처음)
-- generation.revision_count: 이 생성으로 만들어질 게시물의 수정 횟수
ALTER TABLE shortform ADD COLUMN revision_count INT NOT NULL DEFAULT 0;
ALTER TABLE generation ADD COLUMN revision_count INT NOT NULL DEFAULT 0;
