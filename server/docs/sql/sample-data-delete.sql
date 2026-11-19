-- 시연용 샘플 가게(SampleStoreInitializer)와 그 게시물을 지운다. 운영 오픈 전 정리용.
-- 샘플 가게 = 주소가 '(샘플 주소)'로 끝나는 가게 (사장님이 직접 등록한 가게는 해당 없음)
-- ⚠ EC2 .env 의 SAMPLE_DATA_ENABLED 를 먼저 지우고 배포해야 함 (켜 둔 채 재시작하면 다시 들어감)
-- 쿠폰·퀘스트·방문 기록은 store_id 만 들고 있어(FK 없음) 남는다 — 필요하면 같은 조건으로 따로 정리

DELETE FROM shortform_scrap
WHERE shortform_id IN (SELECT id FROM (SELECT f.id FROM shortform f JOIN store s ON s.id = f.store_id
                                        WHERE s.address LIKE '%(샘플 주소)') t);

DELETE FROM shortform_photo
WHERE shortform_id IN (SELECT id FROM (SELECT f.id FROM shortform f JOIN store s ON s.id = f.store_id
                                        WHERE s.address LIKE '%(샘플 주소)') t);

DELETE FROM shortform
WHERE store_id IN (SELECT id FROM (SELECT id FROM store WHERE address LIKE '%(샘플 주소)') t);

DELETE FROM store WHERE address LIKE '%(샘플 주소)';
