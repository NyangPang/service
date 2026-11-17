/**
 * 동네별 실시간 랭킹 (조회순·좋아요순·스크랩순·실제 방문순, 기간 7/14/31일).
 * <p>
 * 원천 데이터: 스크랩 = {@code scrap.entity.ShortformScrap}, 실제 방문 = {@code quest.entity.QuestVisit}(visitDate).
 * 조회수·좋아요는 아직 저장하는 곳이 없어 새 엔티티가 필요하고, 동네 구분을 위해 가게(Store)에 구·동 정보가 필요하다.
 */
package com.commicom.itda.domain.ranking;
