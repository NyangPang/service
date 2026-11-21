package com.commicom.itda.infra.scraping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 지도·카카오맵 공유 링크 → 가게명·주소.
 * 링크 미리보기처럼 공개 장소 페이지 한 장만 읽는다 (네이버: 페이지에 실린 장소 정보, 카카오: og 태그).
 * 못 읽으면 null — 화면은 등록된 가게 정보로 채우고 사장님이 고칠 수 있다.
 */
@Slf4j
@Component
public class MapPlaceReader {

    public record Place(String name, String address) {}

    /** 이 호스트로만 요청 (단축 링크가 다른 곳으로 보내도 따라가지 않음) */
    private static final Set<String> HOSTS = Set.of(
            "naver.me", "map.naver.com", "m.map.naver.com", "m.place.naver.com", "pcmap.place.naver.com",
            "kko.kakao.com", "map.kakao.com", "m.map.kakao.com", "place.map.kakao.com");
    /** 네이버 모바일 장소 페이지는 모바일 브라우저로, 카카오 장소 페이지는 모바일 UA를 막아서 PC 브라우저로 */
    private static final String MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 5;

    /** map.naver.com/p/entry/place/123, m.place.naver.com/restaurant/123/home, ?pinId=123 */
    private static final Pattern NAVER_ID = Pattern.compile("(?:/place/|m\\.place\\.naver\\.com/[a-z]+/|pcmap\\.place\\.naver\\.com/[a-z]+/|[?&]pinId=)(\\d+)");
    /** place.map.kakao.com/123, map.kakao.com/?itemId=123, map.kakao.com/link/map/123 */
    private static final Pattern KAKAO_ID = Pattern.compile("(?:place\\.map\\.kakao\\.com/(?:m/)?|[?&]itemId=|/link/map/)(\\d+)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Place read(String link) {
        try {
            URI uri = allowed(link);
            if (uri == null) return null;
            String url = resolveShortLink(uri);
            if (url == null) return null;

            String naverId = placeId(NAVER_ID, url, "naver.com");
            if (naverId != null) return readNaver(naverId);
            String kakaoId = placeId(KAKAO_ID, url, "kakao.com");
            if (kakaoId != null) return readKakao(kakaoId);
            log.info("지도 링크에서 장소 번호를 못 찾음: {}", url);
            return null;
        } catch (Exception e) {
            log.warn("지도 링크 읽기 실패 (등록된 가게 정보로 대신): {} - {}", link, e.toString());
            return null;
        }
    }

    /** naver.me·kko.kakao.com 단축 링크는 리다이렉트를 따라가 실제 장소 주소로 */
    private String resolveShortLink(URI uri) throws Exception {
        URI cur = uri;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            String host = cur.getHost();
            if (!host.equals("naver.me") && !host.equals("kko.kakao.com")) return cur.toString();
            HttpResponse<Void> res = http.send(get(cur, DESKTOP_UA), HttpResponse.BodyHandlers.discarding());
            String location = res.headers().firstValue("location").orElse(null);
            if (res.statusCode() / 100 != 3 || location == null) return cur.toString();
            cur = allowed(cur.resolve(location).toString());
            if (cur == null) return null;
        }
        return cur.toString();
    }

    private Place readNaver(String id) throws Exception {
        String html = fetch("https://m.place.naver.com/restaurant/" + id + "/home", MOBILE_UA);
        return html == null ? null : parseNaver(html, id);
    }

    private Place readKakao(String id) throws Exception {
        String html = fetch("https://place.map.kakao.com/" + id, DESKTOP_UA);
        return html == null ? null : parseKakao(html);
    }

    /** 네이버 장소 페이지에 실린 장소 정보 (window.__APOLLO_STATE__ = {...}) 의 PlaceDetailBase:{id} */
    static Place parseNaver(String html, String id) {
        int at = html.indexOf("window.__APOLLO_STATE__");
        int start = at < 0 ? -1 : html.indexOf('{', at);
        if (start >= 0) {
            try (JsonParser p = MAPPER.getFactory().createParser(html.substring(start))) {
                JsonNode state = MAPPER.readTree(p);
                JsonNode base = state.path("PlaceDetailBase:" + id);
                if (base.isMissingNode()) base = firstWithPrefix(state, "PlaceDetailBase:");
                if (base != null) {
                    String address = text(base, "roadAddress");
                    if (address == null) address = text(base, "address");
                    Place place = place(text(base, "name"), address);
                    if (place != null) return place;
                }
            } catch (Exception e) {
                log.debug("네이버 장소 정보 파싱 실패: {}", e.toString());
            }
        }
        // 형식이 바뀌었으면 og 제목("가게명 : 네이버")이라도
        return place(og(Jsoup.parse(html), "og:title").replaceFirst("\\s*:\\s*네이버\\s*$", ""), null);
    }

    /** 카카오 장소 페이지: og:title = 가게명, og:description = 주소 */
    static Place parseKakao(String html) {
        Document doc = Jsoup.parse(html);
        return place(og(doc, "og:title"), og(doc, "og:description"));
    }

    private String fetch(String url, String userAgent) throws Exception {
        HttpResponse<String> res = http.send(get(URI.create(url), userAgent), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.info("장소 페이지 응답 {}: {}", res.statusCode(), url);
            return null;
        }
        return res.body();
    }

    private static HttpRequest get(URI uri, String userAgent) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", userAgent)
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .GET().build();
    }

    /** http(s) + 허용 호스트만 */
    static URI allowed(String link) {
        if (link == null || link.isBlank()) return null;
        try {
            URI uri = URI.create(link.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("https") || scheme.equals("http"))) return null;
            return uri.getHost() != null && HOSTS.contains(uri.getHost().toLowerCase()) ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String naverId(String url) {
        return placeId(NAVER_ID, url, "naver.com");
    }

    static String kakaoId(String url) {
        return placeId(KAKAO_ID, url, "kakao.com");
    }

    private static String placeId(Pattern pattern, String url, String domain) {
        if (!url.contains(domain)) return null;
        Matcher m = pattern.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static JsonNode firstWithPrefix(JsonNode state, String prefix) {
        for (Map.Entry<String, JsonNode> e : state.properties()) {
            if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank() ? v.asText().trim() : null;
    }

    private static String og(Document doc, String property) {
        var meta = doc.selectFirst("meta[property=" + property + "]");
        return meta == null ? "" : meta.attr("content").trim();
    }

    private static Place place(String name, String address) {
        String n = name == null || name.isBlank() ? null : name.trim();
        String a = address == null || address.isBlank() ? null : address.trim();
        return n == null && a == null ? null : new Place(n, a);
    }
}
