package com.commicom.itda.domain.review.repository;

import com.commicom.itda.domain.review.entity.ReviewDraw;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDrawRepository extends JpaRepository<ReviewDraw, Long> {

    void deleteByMemberId(Long memberId);
}
