package com.commicom.itda.infra.scraping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapPlaceReaderTest {

    @Test
    void 지도_서비스_링크만_허용() {
        assertThat(MapPlaceReader.allowed("https://naver.me/abc123")).isNotNull();
        assertThat(MapPlaceReader.allowed("https://kko.kakao.com/abc")).isNotNull();
        assertThat(MapPlaceReader.allowed("https://place.map.kakao.com/27531028")).isNotNull();
        assertThat(MapPlaceReader.allowed("http://169.254.169.254/latest/meta-data")).isNull();
        assertThat(MapPlaceReader.allowed("https://naver.me.evil.com/x")).isNull();
        assertThat(MapPlaceReader.allowed("file:///etc/passwd")).isNull();
        assertThat(MapPlaceReader.allowed("")).isNull();
    }

    @Test
    void 링크에서_장소_번호() {
        assertThat(MapPlaceReader.naverId("https://map.naver.com/p/entry/place/11591570?c=15.00,0,0,0,dh")).isEqualTo("11591570");
        assertThat(MapPlaceReader.naverId("https://map.naver.com/p/search/카페/place/1234567")).isEqualTo("1234567");
        assertThat(MapPlaceReader.naverId("https://m.place.naver.com/restaurant/11591570/home")).isEqualTo("11591570");
        assertThat(MapPlaceReader.naverId("https://map.naver.com/p?pinId=998877&pinType=place")).isEqualTo("998877");
        assertThat(MapPlaceReader.kakaoId("https://place.map.kakao.com/27531028")).isEqualTo("27531028");
        assertThat(MapPlaceReader.kakaoId("https://map.kakao.com/?itemId=27531028")).isEqualTo("27531028");
        assertThat(MapPlaceReader.kakaoId("https://map.kakao.com/link/map/27531028")).isEqualTo("27531028");
    }

    @Test
    void 네이버_장소_정보에서_가게명_도로명주소() {
        String html = """
                <html><head><meta property="og:title" content="서경대학교 : 네이버"/></head><body>
                <script>window.__APOLLO_STATE__ = {"Panorama:x":{"id":"x"},"PlaceDetailBase:11591570":{"__typename":"PlaceDetailBase","id":"11591570","name":"서경대학교","road":"버스 {110B}","category":"대학교","roadAddress":"서울 성북구 서경로 124 서경대학교","address":"서울 성북구 정릉동 산16-1"}};
                window.__PLACE_STATE__ = {};</script></body></html>
                """;
        assertThat(MapPlaceReader.parseNaver(html, "11591570"))
                .isEqualTo(new MapPlaceReader.Place("서경대학교", "서울 성북구 서경로 124 서경대학교"));
    }

    @Test
    void 네이버_형식이_바뀌면_og_제목이라도() {
        String html = "<html><head><meta property=\"og:title\" content=\"동네카페 : 네이버\"/></head></html>";
        assertThat(MapPlaceReader.parseNaver(html, "1")).isEqualTo(new MapPlaceReader.Place("동네카페", null));
    }

    @Test
    void 카카오_og_태그에서_가게명_주소() {
        String html = """
                <html><head><title>중앙해장 | 카카오맵</title>
                <meta property="og:title" content="중앙해장">
                <meta property="og:description" content="서울 강남구 영동대로86길 17 육인빌딩 1층">
                </head></html>
                """;
        assertThat(MapPlaceReader.parseKakao(html))
                .isEqualTo(new MapPlaceReader.Place("중앙해장", "서울 강남구 영동대로86길 17 육인빌딩 1층"));
    }
}
