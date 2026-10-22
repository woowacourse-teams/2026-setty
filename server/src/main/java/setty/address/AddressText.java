package setty.address;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AddressText {

    private static final Set<String> SEOUL_DISTRICTS = Set.of(
            "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구",
            "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구",
            "용산구", "은평구", "종로구", "중구", "중랑구");
    private static final Set<String> SEOUL_NAMES = Set.of("서울", "서울시", "서울특별시");
    private static final Set<String> OTHER_PROVINCES = Set.of(
            "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남",
            "경북", "경남", "제주",
            "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시", "세종특별자치시",
            "경기도", "강원도", "강원특별자치도", "충청북도", "충청남도", "전라북도", "전북특별자치도", "전라남도",
            "경상북도", "경상남도", "제주도", "제주특별자치도");
    private static final int MAX_DETAIL_LENGTH = 100;

    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*(\\)|$)");
    private static final Pattern DASHES = Pattern.compile("[‐‑‒–—―−]");
    private static final Pattern SEOUL_PREFIX = Pattern.compile("^서울(?:특별시|시)?(?:\\s+|$|(?=[가-힣]+구(?:\\s|$)))");
    // "논현로 12길", "고척로 52마길"처럼 띄어 쓴 길 번호를 도로명 하나로 붙인다.
    private static final Pattern SPACED_ROAD_NUMBER = Pattern.compile("([가-힣0-9][가-힣0-9.]*(?:로|길))\\s*(\\d+)\\s*([가-힣]?길)(?=\\s|$)");
    private static final Pattern ROAD = Pattern.compile(
            "(?<![가-힣0-9.])([가-힣0-9][가-힣0-9.]*(?:로|길))\\s*(지하\\s*)?(\\d{1,5})(?:\\s*-\\s*(\\d{1,5}))?(?:번(?![지길]))?(?![0-9가-힣.-])");
    private static final Pattern JIBUN = Pattern.compile(
            "(?<![가-힣0-9.])([가-힣][가-힣0-9.]*(?:동|가|로))\\s*(산\\s*)?(\\d{1,4})(?:\\s*-\\s*(\\d{1,4}))?(?:\\s*번지?)?(?![0-9가-힣.-])");

    // special은 도로명이면 지하, 지번이면 산 여부다.
    record Candidate(String district, String name, boolean special, int main, int sub, String detail) {
    }

    private AddressText() {
    }

    static String normalize(final String freeText) {
        String text = Normalizer.normalize(freeText, Normalizer.Form.NFKC);
        text = PARENTHESES.matcher(text).replaceAll(" ");
        text = DASHES.matcher(text).replaceAll("-");
        text = text.replace(',', ' ').replaceAll("\\s+", " ").strip();
        text = SEOUL_PREFIX.matcher(text).replaceFirst("");
        return SPACED_ROAD_NUMBER.matcher(text).replaceAll("$1$2$3");
    }

    static List<Candidate> roads(final String normalized) {
        return candidates(ROAD, normalized);
    }

    static List<Candidate> jibuns(final String normalized) {
        return candidates(JIBUN, normalized);
    }

    private static List<Candidate> candidates(final Pattern pattern, final String text) {
        final List<Candidate> candidates = new ArrayList<>();
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            final String[] prefix = text.substring(0, matcher.start()).split(" ");
            if (outsideSeoul(prefix)) {
                continue;
            }
            candidates.add(new Candidate(
                    district(prefix),
                    matcher.group(1),
                    matcher.group(2) != null,
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4)),
                    detail(text.substring(matcher.end()))
            ));
        }
        return candidates;
    }

    private static boolean outsideSeoul(final String[] tokens) {
        for (final String token : tokens) {
            if (SEOUL_NAMES.contains(token) || SEOUL_DISTRICTS.contains(token)) {
                continue;
            }
            // 서울 동·도로 이름은 시·군·구로 끝나지 않는다. 다만 "○○도"는 여의도, "○○입구"는 역 이름과 겹쳐 규칙에서 뺀다.
            if (OTHER_PROVINCES.contains(token) || token.matches("[가-힣]+(?:시|군)")
                    || token.matches("[가-힣]+구") && !token.endsWith("입구")) {
                return true;
            }
        }
        return false;
    }

    private static String district(final String[] tokens) {
        for (final String token : tokens) {
            if (SEOUL_DISTRICTS.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private static String detail(final String rest) {
        final String detail = rest.strip();
        if (detail.isEmpty()) {
            return null;
        }
        return detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) : detail;
    }
}
