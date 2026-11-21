package com.commicom.itda.domain.shortform.repository;

import com.commicom.itda.domain.shortform.entity.ShortformView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ShortformViewRepository extends JpaRepository<ShortformView, Long> {

    boolean existsByShortformIdAndMemberIdAndViewDate(Long shortformId, Long memberId, LocalDate viewDate);

    /** 기간 안 가게 조회수 (KST 날짜, 양 끝 포함) */
    long countByStoreIdAndViewDateBetween(Long storeId, LocalDate from, LocalDate to);

    /** 방문율 계산용: 로그인 손님의 조회 기록 */
    List<ShortformView> findAllByStoreIdAndMemberIdIsNotNullAndViewDateBetween(Long storeId, LocalDate from, LocalDate to);

    /** 게시물별 전체 조회수. 원소: [shortformId(Long), count(Long)] */
    @Query("select v.shortformId, count(v) from ShortformView v where v.storeId = :storeId group by v.shortformId")
    List<Object[]> countByShortform(@Param("storeId") Long storeId);

    /** 탈퇴: 가게 조회수는 남기고 누가 봤는지만 지움 */
    @Modifying
    @Query("update ShortformView v set v.memberId = null where v.memberId = :memberId")
    void anonymize(@Param("memberId") Long memberId);
}
