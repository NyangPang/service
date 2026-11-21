package com.commicom.itda.domain.generation.service;

import com.commicom.itda.domain.pro.entity.ProSubscription;
import com.commicom.itda.domain.pro.repository.ProSubscriptionRepository;
import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import com.commicom.itda.global.util.KstTime;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationServiceRevisionTest {

    private final ShortformRepository shortforms = mock(ShortformRepository.class);
    private final ProSubscriptionRepository pros = mock(ProSubscriptionRepository.class);
    private final GenerationService service =
            new GenerationService(null, null, null, null, null, null, shortforms, pros);

    private final Store store = store(1L);

    @Test
    void 새로_만들기는_0() {
        assertThat(service.nextRevisionCount(store, null)).isZero();
    }

    @Test
    void 수정하면_고칠_게시물_횟수에_1을_더한다() {
        base(10L, store, 2);
        assertThat(service.nextRevisionCount(store, 10L)).isEqualTo(3);
    }

    @Test
    void 무료는_3번_고친_게시물을_더_고칠_수_없다() {
        base(10L, store, 3);
        assertThatThrownBy(() -> service.nextRevisionCount(store, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GENERATION_REVISION_LIMIT));
    }

    @Test
    void PRO는_횟수_제한이_없다() {
        base(10L, store, 7);
        when(pros.findByStoreId(1L)).thenReturn(Optional.of(
                new ProSubscription(1L, KstTime.now().minusDays(1), KstTime.now().plusDays(10))));
        assertThat(service.nextRevisionCount(store, 10L)).isEqualTo(8);
    }

    @Test
    void 만료된_PRO는_무료와_같다() {
        base(10L, store, 3);
        when(pros.findByStoreId(1L)).thenReturn(Optional.of(
                new ProSubscription(1L, KstTime.now().minusDays(40), KstTime.now().minusDays(10))));
        assertThatThrownBy(() -> service.nextRevisionCount(store, 10L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 다른_가게_게시물은_고칠_수_없다() {
        base(10L, store(2L), 0);
        assertThatThrownBy(() -> service.nextRevisionCount(store, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORE_FORBIDDEN));
    }

    private void base(Long id, Store owner, int revisionCount) {
        Shortform s = Shortform.builder().store(owner).imageUrl("x.jpg").title("t").revisionCount(revisionCount).build();
        when(shortforms.findById(id)).thenReturn(Optional.of(s));
    }

    private static Store store(Long id) {
        Store s = mock(Store.class);
        when(s.getId()).thenReturn(id);
        return s;
    }
}
