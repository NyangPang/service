package com.commicom.itda.domain.shortform.entity;

import com.commicom.itda.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 게시물 조회 기록 (사장님 성과지표용). 피드에서 2초 이상 본 게시물마다 한 줄.
 * 로그인한 손님은 같은 게시물을 하루 1번만 센다. 비로그인은 memberId 없이 앱이 한 번 실행되는 동안 1번.
 * 게시물이 지워져도 가게 조회수에는 남도록 storeId를 같이 저장하고 FK는 걸지 않는다 (quest_visit과 같은 방식).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(columnList = "storeId, viewDate"),
        @Index(columnList = "shortformId, memberId, viewDate")
})
public class ShortformView extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shortformId;

    @Column(nullable = false)
    private Long storeId;

    /** 본 손님 (비로그인이면 null, 탈퇴하면 null로 바뀜) */
    private Long memberId;

    /** KST 날짜 */
    @Column(nullable = false)
    private LocalDate viewDate;

    public ShortformView(Long shortformId, Long storeId, Long memberId, LocalDate viewDate) {
        this.shortformId = shortformId;
        this.storeId = storeId;
        this.memberId = memberId;
        this.viewDate = viewDate;
    }
}
