package com.commicom.itda.global.init;

import com.commicom.itda.domain.shortform.entity.Shortform;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.entity.StoreCategory;
import com.commicom.itda.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 시연용 샘플 가게: 서울 25개 자치구 × 5곳 = 125곳, 가게마다 홍보 영상 게시물 1개.
 * 사진·영상은 S3 nyang-pang/sample/ 에 올려 둔 파일 (구마다 Group1~5.png 를 한 장씩, 영상은 모두 test.mp4).
 * <p>
 * sample-data.enabled=true 일 때만 실행 (local 기본 켜짐, 운영은 EC2 .env 에 SAMPLE_DATA_ENABLED=true).
 * 이미 들어가 있으면(첫 샘플 가게 이름이 있으면) 아무것도 하지 않아서 켜 둔 채 재시작해도 중복되지 않는다.
 * 나중에 지우려면: 주소가 '(샘플 주소)'로 끝나는 가게와 그 게시물 (server/docs/sql/sample-data-delete.sql).
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sample-data.enabled", havingValue = "true")
public class SampleStoreInitializer implements ApplicationRunner {

    static final int STORES_PER_DISTRICT = 5;
    static final String SAMPLE_VIDEO = "test.mp4";

    private final StoreRepository storeRepository;
    private final ShortformRepository shortformRepository;

    /** 업로드 CloudFront 도메인 (S3 nyang-pang) */
    @Value("${aws.cloudfront.domain}")
    private String cloudfrontDomain;

    /** 자치구 · 대표 동 · 구청 근처 좌표 (가게는 이 주변 약 500m 안에 흩어 둠) */
    record District(String gu, String dong, double lat, double lng) {
        /** 가게 이름 앞에 붙일 동네 이름 (예: 역삼동 → 역삼) */
        String shortDong() {
            return dong.endsWith("동") ? dong.substring(0, dong.length() - 1) : dong;
        }
    }

    static final List<District> DISTRICTS = List.of(
            new District("강남구", "역삼동", 37.5172, 127.0473),
            new District("강동구", "천호동", 37.5301, 127.1238),
            new District("강북구", "미아동", 37.6396, 127.0257),
            new District("강서구", "화곡동", 37.5509, 126.8495),
            new District("관악구", "봉천동", 37.4781, 126.9515),
            new District("광진구", "자양동", 37.5385, 127.0823),
            new District("구로구", "구로동", 37.4954, 126.8874),
            new District("금천구", "가산동", 37.4569, 126.8955),
            new District("노원구", "상계동", 37.6542, 127.0568),
            new District("도봉구", "쌍문동", 37.6688, 127.0471),
            new District("동대문구", "회기동", 37.5744, 127.0396),
            new District("동작구", "노량진동", 37.5124, 126.9393),
            new District("마포구", "망원동", 37.5663, 126.9019),
            new District("서대문구", "연희동", 37.5791, 126.9368),
            new District("서초구", "방배동", 37.4837, 127.0324),
            new District("성동구", "성수동", 37.5633, 127.0371),
            new District("성북구", "안암동", 37.5894, 127.0167),
            new District("송파구", "잠실동", 37.5145, 127.1059),
            new District("양천구", "목동", 37.5170, 126.8665),
            new District("영등포구", "문래동", 37.5264, 126.8962),
            new District("용산구", "이태원동", 37.5324, 126.9907),
            new District("은평구", "응암동", 37.6027, 126.9291),
            new District("종로구", "익선동", 37.5735, 126.9790),
            new District("중구", "을지로동", 37.5641, 126.9979),
            new District("중랑구", "면목동", 37.6066, 127.0927)
    );

    /** 업종별 가게 이름(동네 이름 뒤에 붙음)과 게시물 소개 글. 구마다 돌려 써서 이름이 겹치지 않게 */
    record Kind(String name, String caption) {
    }

    static final List<Kind> RESTAURANTS = List.of(
            new Kind("손칼국수", "매일 아침 직접 미는 쫄깃한 면발, 진한 바지락 국물 한 그릇 🍜"),
            new Kind("할매순대국", "40년 끓여 온 뽀얀 국물, 든든한 한 끼는 여기서!"),
            new Kind("숯불갈비", "참숯에 구워 더 맛있는 양념갈비, 가족 외식 장소로 딱 🔥"),
            new Kind("돈까스 연구소", "겉바속촉 두툼한 수제 돈까스, 소스까지 직접 만들어요"),
            new Kind("마라탕", "원하는 재료 골라 담고 맵기는 취향대로! 🌶️"),
            new Kind("쌀국수", "12시간 우린 양지 육수로 만든 진한 쌀국수"),
            new Kind("수제버거", "매일 굽는 번과 두툼한 패티, 육즙 가득 수제버거 🍔"),
            new Kind("김밥", "재료 아끼지 않은 꽉 찬 김밥, 포장도 빠르게!"),
            new Kind("생선구이", "화덕에 구운 고등어·삼치, 정갈한 백반 한 상"),
            new Kind("닭한마리", "칼칼한 국물에 부드러운 닭, 마지막은 칼국수 사리로"),
            new Kind("부대찌개", "햄 듬뿍 진한 부대찌개, 라면 사리는 무한!"),
            new Kind("초밥", "그날 들어온 신선한 생선으로 쥐는 초밥 🍣"),
            new Kind("평양냉면", "슴슴하고 깊은 육수, 여름엔 꼭 들러 주세요"),
            new Kind("감자탕", "푸짐한 등뼈에 시래기 듬뿍, 볶음밥까지 완벽"),
            new Kind("파스타 키친", "생면으로 만드는 크림·토마토 파스타 🍝"),
            new Kind("떡볶이", "쫀득한 쌀떡에 달콤매콤 소스, 튀김은 필수!"),
            new Kind("보쌈", "야들야들 수육과 아삭한 김치의 조합"),
            new Kind("곱창", "고소한 곱이 꽉 찬 한우 곱창, 퇴근길 한잔에 딱"),
            new Kind("짬뽕", "불맛 가득 해물짬뽕, 탕수육도 함께 즐겨 보세요"),
            new Kind("비빔밥", "제철 나물 듬뿍 올린 건강한 돌솥비빔밥"),
            new Kind("카레", "스파이스를 직접 볶아 만든 일본식 카레 🍛"),
            new Kind("족발", "매일 삶아 내는 쫄깃한 족발, 막국수 서비스!"),
            new Kind("해장국", "뜨끈한 선지해장국으로 하루를 시작하세요"),
            new Kind("텐동", "바삭하게 튀긴 새우·채소 튀김 덮밥"),
            new Kind("샤브샤브", "신선한 채소와 소고기, 마무리는 죽으로"),
            new Kind("닭갈비", "철판에 볶는 매콤한 춘천식 닭갈비"),
            new Kind("한식뷔페", "매일 바뀌는 집밥 반찬, 든든한 점심 뷔페"),
            new Kind("양꼬치", "숯불에 구운 양꼬치와 칭따오 한잔 🍢"),
            new Kind("규동", "달콤 짭짤한 소고기 덮밥, 혼밥하기 좋아요"),
            new Kind("포케", "신선한 연어와 채소를 담은 가벼운 한 그릇 🥗")
    );

    static final List<Kind> CAFES = List.of(
            new Kind("로스터리 카페", "직접 볶은 원두로 내리는 핸드드립 ☕"),
            new Kind("소금빵 공방", "버터 향 가득한 소금빵, 오전에 다 팔려요 🥐"),
            new Kind("디저트 카페", "매일 굽는 케이크와 시즌 디저트"),
            new Kind("베이커리", "천연 발효종으로 굽는 건강한 식빵과 깜빠뉴 🍞"),
            new Kind("이자카야", "퇴근 후 하이볼 한잔, 따뜻한 안주와 함께 🍶"),
            new Kind("브런치 카페", "주말 아침엔 에그베네딕트와 라떼 한 잔"),
            new Kind("수제 맥주집", "직접 양조한 크래프트 맥주 6종 🍺"),
            new Kind("도넛 가게", "갓 튀긴 폭신한 도넛, 필링은 고르는 재미가 있어요 🍩")
    );

    static final List<Kind> FOOD_RETAILS = List.of(
            new Kind("정육점", "매일 들여오는 신선한 한우·한돈 🥩"),
            new Kind("반찬가게", "엄마 손맛 그대로, 매일 만드는 반찬 20가지"),
            new Kind("과일가게", "산지에서 바로 온 제철 과일 🍓"),
            new Kind("떡집", "새벽에 찐 말랑한 떡, 답례떡 주문도 받아요"),
            new Kind("수산시장", "오늘 들어온 활어와 제철 해산물 🐟")
    );

    /** 구마다 다섯 번째 가게 업종 (식당 위주에 다른 업종 몇 곳 섞기) */
    static final List<StoreCategory> FIFTH_CATEGORIES = List.of(
            StoreCategory.RESTAURANT, StoreCategory.BEAUTY, StoreCategory.RESTAURANT, StoreCategory.PET,
            StoreCategory.CAFE_BAKERY_PUB, StoreCategory.HOBBY_LEISURE, StoreCategory.RESTAURANT, StoreCategory.LIVING,
            StoreCategory.FASHION
    );

    static final List<Kind> OTHERS = List.of(
            new Kind("헤어살롱", "트렌디한 컷과 펌, 첫 방문 10% 할인 💇"),
            new Kind("펫샵", "반려동물 용품과 수제 간식이 가득 🐶"),
            new Kind("공방", "나만의 향초·도자기를 만들어 보세요 🕯️"),
            new Kind("생활잡화점", "예쁘고 쓸모 있는 살림살이 모음"),
            new Kind("빈티지 옷가게", "하나뿐인 빈티지 셔츠와 가방 👜")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SampleStore> samples = build();
        String first = samples.get(0).name();
        if (storeRepository.findAll().stream().anyMatch(s -> s.getName().equals(first))) {
            log.info("[샘플 데이터] 이미 있어서 건너뜀 ({}곳)", samples.size());
            return;
        }
        List<Store> stores = storeRepository.saveAll(samples.stream().map(this::toStore).toList());
        List<Shortform> shortforms = new ArrayList<>();
        for (int i = 0; i < stores.size(); i++) {
            SampleStore sample = samples.get(i);
            shortforms.add(Shortform.builder()
                    .store(stores.get(i))
                    .imageUrl(asset(sample.photo()))
                    .videoUrl(asset(SAMPLE_VIDEO))
                    .title(sample.name())
                    .caption(sample.caption())
                    .published(true)
                    .build());
        }
        shortformRepository.saveAll(shortforms);
        log.info("[샘플 데이터] 가게 {}곳 · 영상 게시물 {}개 추가", stores.size(), shortforms.size());
    }

    /** 샘플 가게 한 곳 (DB 와 상관없는 값) */
    record SampleStore(String name, StoreCategory category, String address, double lat, double lng,
                       String description, String caption, String photo) {
    }

    /** 125곳을 항상 같은 순서·같은 값으로 만든다 (다시 만들어도 같은 결과) */
    static List<SampleStore> build() {
        List<SampleStore> list = new ArrayList<>();
        int restaurant = 0;
        int cafe = 0;
        int foodRetail = 0;
        int other = 0;
        for (int d = 0; d < DISTRICTS.size(); d++) {
            District district = DISTRICTS.get(d);
            StoreCategory fifth = FIFTH_CATEGORIES.get(d % FIFTH_CATEGORIES.size());
            List<StoreCategory> categories = List.of(StoreCategory.RESTAURANT, StoreCategory.RESTAURANT,
                    StoreCategory.CAFE_BAKERY_PUB, StoreCategory.FOOD_RETAIL, fifth);
            for (int i = 0; i < STORES_PER_DISTRICT; i++) {
                StoreCategory category = categories.get(i);
                Kind kind = switch (category) {
                    case RESTAURANT -> RESTAURANTS.get(restaurant++ % RESTAURANTS.size());
                    case CAFE_BAKERY_PUB -> CAFES.get(cafe++ % CAFES.size());
                    case FOOD_RETAIL -> FOOD_RETAILS.get(foodRetail++ % FOOD_RETAILS.size());
                    default -> OTHERS.get(other++ % OTHERS.size());
                };
                // 구청 근처에 오각형으로 흩어 둠 (약 300~500m)
                double angle = 2 * Math.PI * i / STORES_PER_DISTRICT + d;
                double radius = 0.003 + 0.0006 * i;
                list.add(new SampleStore(
                        district.shortDong() + " " + kind.name(),
                        category,
                        "서울 " + district.gu() + " " + district.dong() + " (샘플 주소)",
                        round(district.lat() + radius * Math.sin(angle)),
                        round(district.lng() + radius * 1.25 * Math.cos(angle)),
                        kind.caption(),
                        kind.caption(),
                        "Group" + (i + 1) + ".png"));
            }
        }
        return list;
    }

    private Store toStore(SampleStore s) {
        return Store.builder()
                .name(s.name())
                .category(s.category())
                .address(s.address())
                .latitude(s.lat())
                .longitude(s.lng())
                .businessHours("매일 11:00-21:00")
                .description(s.description())
                .thumbnailUrl(asset(s.photo()))
                .build();
    }

    private String asset(String file) {
        return "https://" + cloudfrontDomain + "/sample/" + file;
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000) / 1_000_000.0;
    }
}
