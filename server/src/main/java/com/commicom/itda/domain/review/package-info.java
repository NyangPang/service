/**
 * 리뷰 (손님 사진·영상 + 글, 비공개 선택, 작성 보상 뽑기).
 * <p>
 * 구조는 다른 도메인과 같음: controller / service / repository / entity / dto.
 * 보상 뽑기는 {@code pigeon.service.PigeonService} 의 레벨업 뽑기, 파일 업로드는 {@code infra.storage.StorageService} 재사용.
 * 마이페이지(손님: 내 리뷰 수정·삭제 / 사장님: 리뷰 숨기기)도 이 도메인의 API로 만든다.
 */
package com.commicom.itda.domain.review;
