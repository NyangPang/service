package com.commicom.itda.domain.review.repository;

import com.commicom.itda.domain.member.entity.Member;
import com.commicom.itda.domain.review.entity.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"member", "store"})
    List<Review> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    @EntityGraph(attributePaths = {"member", "store"})
    List<Review> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<Review> findAllByMember(Member member);

    boolean existsByVisitId(Long visitId);
}
