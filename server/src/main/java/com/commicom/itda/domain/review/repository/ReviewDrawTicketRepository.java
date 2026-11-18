package com.commicom.itda.domain.review.repository;

import com.commicom.itda.domain.review.entity.ReviewDrawTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewDrawTicketRepository extends JpaRepository<ReviewDrawTicket, Long> {

    boolean existsByVisitId(Long visitId);

    Optional<ReviewDrawTicket> findByReviewId(Long reviewId);

    /** 뽑기: 같은 뽑기권으로 동시에 두 번 뽑지 못하게 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReviewDrawTicket t where t.reviewId = :reviewId")
    Optional<ReviewDrawTicket> findByReviewIdForUpdate(@Param("reviewId") Long reviewId);

    List<ReviewDrawTicket> findAllByVisitIdIn(Collection<Long> visitIds);

    List<ReviewDrawTicket> findAllByReviewIdIn(Collection<Long> reviewIds);

    void deleteByMemberId(Long memberId);
}
