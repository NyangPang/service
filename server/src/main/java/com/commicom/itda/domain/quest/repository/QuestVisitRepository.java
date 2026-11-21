package com.commicom.itda.domain.quest.repository;

import com.commicom.itda.domain.quest.entity.QuestVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface QuestVisitRepository extends JpaRepository<QuestVisit, Long> {

    List<QuestVisit> findAllByMemberId(Long memberId);

    /** 성과지표: 가게의 기간 안 방문 인증 (KST 날짜, 양 끝 포함) */
    List<QuestVisit> findAllByStoreIdAndVisitDateBetween(Long storeId, LocalDate from, LocalDate to);

    boolean existsByMemberIdAndStoreIdAndVisitDate(Long memberId, Long storeId, LocalDate visitDate);

    boolean existsByMemberIdAndQuestIdAndStoreId(Long memberId, Long questId, Long storeId);

    int countByMemberIdAndQuestId(Long memberId, Long questId);

    void deleteByMemberId(Long memberId);

    /** 가게별 방문 인증 수 (from 이후, KST 날짜). 원소: [storeId(Long), count(Long)] — 방문이 없는 가게는 빠짐 */
    @Query("""
            select v.storeId, count(v) from QuestVisit v
            where v.storeId in :storeIds and v.visitDate >= :from
            group by v.storeId
            """)
    List<Object[]> countByStoreSince(@Param("storeIds") Collection<Long> storeIds, @Param("from") LocalDate from);
}
