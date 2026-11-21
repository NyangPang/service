package com.commicom.itda.domain.shortform.service;

import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.entity.ShortformView;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.shortform.repository.ShortformViewRepository;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import com.commicom.itda.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ShortformViewService {

    private final ShortformRepository shortformRepository;
    private final ShortformViewRepository viewRepository;

    /**
     * 게시물 조회 1회 기록. 공개 전 게시물과 사장님이 자기 가게 게시물을 본 것은 세지 않는다.
     * 로그인 손님은 같은 게시물을 하루 1번만.
     *
     * @return 이번에 새로 셌으면 true
     */
    @Transactional
    public boolean record(Long shortformId, Long memberId) {
        Shortform shortform = shortformRepository.findById(shortformId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHORTFORM_NOT_FOUND));
        if (!shortform.isPublished() || (memberId != null && shortform.isOwnedBy(memberId))) return false;
        LocalDate today = KstTime.today();
        if (memberId != null && viewRepository.existsByShortformIdAndMemberIdAndViewDate(shortformId, memberId, today)) {
            return false;
        }
        viewRepository.save(new ShortformView(shortformId, shortform.getStore().getId(), memberId, today));
        return true;
    }
}
