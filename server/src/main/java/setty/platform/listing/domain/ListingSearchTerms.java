package setty.platform.listing.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 검색어를 제목·설명에서 찾을 단어들과 함께 볼 카테고리로 푼다.
 * 검색어가 동의어 묶음의 한 단어면 묶음의 모든 단어로 찾고 그 카테고리의 매물도 함께 준다.
 * 묶음에 없는 검색어는 그 단어 하나로만 찾는다.
 */
public record ListingSearchTerms(List<String> words, ListingCategory category) {

    private static final Map<ListingCategory, List<String>> SYNONYMS = Map.of(
            ListingCategory.SOFA, List.of("소파", "쇼파", "카우치", "sofa", "couch"),
            ListingCategory.TABLE, List.of("테이블", "탁자", "식탁", "좌탁", "티테이블", "table"),
            ListingCategory.DESK, List.of("책상", "데스크", "컴퓨터책상", "desk"),
            ListingCategory.CHAIR, List.of("의자", "체어", "스툴", "chair", "stool"),
            ListingCategory.STORAGE, List.of("수납장", "서랍장", "서랍", "옷장", "장롱", "책장", "선반", "수납", "storage"),
            ListingCategory.BED, List.of("침대", "매트리스", "프레임", "bed", "mattress")
    );

    public static ListingSearchTerms of(String keyword) {
        String word = keyword.strip().toLowerCase(Locale.ROOT);
        return SYNONYMS.entrySet().stream()
                .filter(group -> group.getValue().contains(word))
                .findFirst()
                .map(group -> new ListingSearchTerms(group.getValue(), group.getKey()))
                .orElseGet(() -> new ListingSearchTerms(List.of(word), null));
    }
}
