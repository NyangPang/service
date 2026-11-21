package com.commicom.itda.domain.shortform.entity;

import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.global.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shortform extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Store store;

    /** AI 생성 이미지 URL. 영상 게시물이면 영상의 포스터(썸네일)로 쓴다 */
    @Column(nullable = false)
    private String imageUrl;

    /** 홍보 영상 URL (영상 게시물만, 없으면 null). 있으면 피드에서 사진 대신 영상을 재생한다 */
    @Column(length = 1000)
    private String videoUrl;

    @Column(nullable = false, length = 200)
    private String title;

    /** 피드 게시물 소개 글 — 사장님 어필을 AI로 다듬은 문장 (없으면 null, 화면은 title) */
    @Column(length = 500)
    private String caption;

    /** 사장님이 올린 메뉴판·음식·가게 사진 (AI 사진 뒤에 붙어 옆으로 넘겨 봄, 최대 MAX_PHOTOS) */
    @ElementCollection
    @CollectionTable(name = "shortform_photo", joinColumns = @JoinColumn(name = "shortform_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 1000)
    private List<String> photoUrls = new ArrayList<>();

    /** 손님 피드에 보이는지. AI 생성 직후엔 false → 사장님이 [업로드]하면 true */
    @Column(nullable = false)
    private boolean published;

    /** 처음 만든 게시물에서 몇 번 고쳐 만든 버전인지 (0 = 처음). 무료는 FREE_REVISIONS 번까지, PRO는 무제한 */
    @Column(nullable = false)
    private int revisionCount;

    public static final int FREE_REVISIONS = 1;

    /** 생성 요청의 수정 횟수를 이어받음 (새로 만들기 0, 수정이면 고칠 게시물 + 1) */
    public void markRevision(int revisionCount) {
        this.revisionCount = revisionCount;
    }

    /** 게시물 한 개의 사진은 AI 사진 1장 + 사장님 사진 4장까지 */
    public static final int MAX_PHOTOS = 4;

    @Builder
    private Shortform(Store store, String imageUrl, String videoUrl, String title, String caption, List<String> photoUrls,
                      Boolean published, Integer revisionCount) {
        this.store = store;
        this.imageUrl = imageUrl;
        this.videoUrl = videoUrl;
        this.title = title;
        this.caption = caption;
        // 따로 정하지 않으면 공개 (샘플 데이터 등). AI 생성 결과는 false 로 만듦
        this.published = published == null || published;
        this.revisionCount = revisionCount == null ? 0 : revisionCount;
        if (photoUrls != null) {
            this.photoUrls = new ArrayList<>(photoUrls.stream().limit(MAX_PHOTOS).toList());
        }
    }

    public void publish() {
        this.published = true;
    }

    public boolean isOwnedBy(Long memberId) {
        return store.isOwnedBy(memberId);
    }

    /** 게시물에서 옆으로 넘겨 볼 사진 전체: AI 사진이 첫 장 */
    public List<String> getImageUrls() {
        return Stream.concat(Stream.of(imageUrl), photoUrls.stream()).toList();
    }
}
