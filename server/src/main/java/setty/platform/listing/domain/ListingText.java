package setty.platform.listing.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 매물 제목·설명을 두 글자 묶음(bigram) 집합으로 바꿔 다른 매물과 얼마나 겹치는지 잰다.
 * 띄어쓰기와 조사가 제각각인 한국어 제목에서 단어 단위보다 잘 맞는다("4인 원목식탁"과 "원목 식탁 4인용").
 * 제목은 짧고 핵심 단어가 모여 있어 설명보다 두 배로 친다.
 * "4인용"은 "4인"으로 줄여 잰다. 그대로 두면 "4인용"과 "6인용"이 "인용"으로 겹친다.
 * 같은 물건의 다른 표기는 하나로 바꿔 잰다("식탁" → "테이블", "쇼파" → "소파").
 * 검색 동의어 묶음({@link ListingSearchTerms})은 같은 카테고리의 다른 물건(침대·매트리스·프레임)까지 묶으므로 점수에는 쓰지 않는다.
 */
public final class ListingText {

    private static final double TITLE_WEIGHT = 2.0;
    private static final Pattern TRADE_WORDS = Pattern.compile(
            "팝니다|판매합니다|판매|급처분|급처|급매|직거래|택배|네고|가능|상태|좋아요|깨끗|거의|사용감|[0-9][0-9,]*\\s*만?\\s*원|[sabc]급");
    private static final Pattern SEATS = Pattern.compile("([0-9]+)\\s*인\\s*용");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final List<List<String>> SAME_WORDS = List.of(
            List.of("소파", "쇼파", "카우치", "sofa", "couch"),
            List.of("테이블", "식탁", "탁자", "table"),
            List.of("책상", "데스크", "desk"),
            List.of("의자", "체어", "chair"),
            List.of("스툴", "stool"),
            List.of("옷장", "장롱"),
            List.of("서랍장", "서랍"),
            List.of("침대", "bed"),
            List.of("매트리스", "mattress")
    );
    private static final Map<String, String> REPRESENTATIVES = SAME_WORDS.stream()
            .flatMap(group -> group.stream().map(word -> Map.entry(word, group.getFirst())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    // 영어 낱말은 통째로만 바꾼다("portable"의 "table"은 그대로). 한글은 띄어쓰기 없이 붙으므로 경계를 보지 않는다.
    private static final Pattern SAME_WORD = Pattern.compile(REPRESENTATIVES.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(word -> word.matches("[a-z]+") ? "\\b" + word + "\\b" : Pattern.quote(word))
            .collect(Collectors.joining("|")));

    private final Set<String> titleBigrams;
    private final Set<String> descriptionBigrams;

    private ListingText(Set<String> titleBigrams, Set<String> descriptionBigrams) {
        this.titleBigrams = titleBigrams;
        this.descriptionBigrams = descriptionBigrams;
    }

    public static ListingText of(String title, String description) {
        return new ListingText(bigramsOf(title), bigramsOf(description));
    }

    public double similarityTo(ListingText other) {
        return TITLE_WEIGHT * jaccard(titleBigrams, other.titleBigrams)
                + jaccard(descriptionBigrams, other.descriptionBigrams);
    }

    private static Set<String> bigramsOf(String text) {
        Set<String> bigrams = new HashSet<>();
        if (text == null) {
            return bigrams;
        }
        String withoutTradeWords = TRADE_WORDS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        String withSeats = SEATS.matcher(withoutTradeWords).replaceAll("$1인");
        String cleaned = SAME_WORD.matcher(withSeats).replaceAll(match -> " " + REPRESENTATIVES.get(match.group()) + " ");
        for (String word : SEPARATORS.split(cleaned)) {
            for (int index = 0; index + 2 <= word.length(); index++) {
                bigrams.add(word.substring(index, index + 2));
            }
        }
        return bigrams;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        long shared = left.stream().filter(right::contains).count();
        return (double) shared / (left.size() + right.size() - shared);
    }
}
