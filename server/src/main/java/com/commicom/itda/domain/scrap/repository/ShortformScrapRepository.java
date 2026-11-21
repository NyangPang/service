package com.commicom.itda.domain.scrap.repository;

import com.commicom.itda.domain.member.entity.Member;
import com.commicom.itda.domain.scrap.entity.ShortformScrap;
import com.commicom.itda.domain.shortform.entity.Shortform;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShortformScrapRepository extends JpaRepository<ShortformScrap, Long> {

    /** 목록에서 숏폼·가게 이름을 같이 쓰므로 한 번에 불러옴 */
    @EntityGraph(attributePaths = {"shortform", "shortform.store"})
    List<ShortformScrap> findAllByMemberOrderByCreatedAtDesc(Member member);

    boolean existsByMemberAndShortform(Member member, Shortform shortform);

    Optional<ShortformScrap> findByMemberAndShortform(Member member, Shortform shortform);

    void deleteAllByMember(Member member);

    List<ShortformScrap> findAllByShortform(Shortform shortform);

    /** 성과지표: 가게 게시물이 [from, to) 사이에 스크랩된 수 (createdAt 기준, 서버 시각) */
    @Query("select count(s) from ShortformScrap s where s.shortform.store.id = :storeId and s.createdAt >= :from and s.createdAt < :to")
    long countByStoreBetween(@Param("storeId") Long storeId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 성과지표: 게시물별 현재 스크랩 수. 원소: [shortformId(Long), count(Long)] */
    @Query("select s.shortform.id, count(s) from ShortformScrap s where s.shortform.store.id = :storeId group by s.shortform.id")
    List<Object[]> countByShortform(@Param("storeId") Long storeId);

    /** 숏폼을 지울 때 그 숏폼의 스크랩도 함께 지움 */
    void deleteAllByShortform(Shortform shortform);
}
