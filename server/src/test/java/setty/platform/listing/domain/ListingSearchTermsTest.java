package setty.platform.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListingSearchTermsTest {

    @Test
    void 묶음_단어는_묶음의_모든_단어와_카테고리로_푼다() {
        ListingSearchTerms terms = ListingSearchTerms.of("탁자");

        assertThat(terms.words()).containsExactly("테이블", "탁자", "식탁", "좌탁", "티테이블", "table");
        assertThat(terms.category()).isEqualTo(ListingCategory.TABLE);
    }

    @Test
    void 앞뒤_공백과_대소문자를_정리해서_묶음을_찾는다() {
        ListingSearchTerms terms = ListingSearchTerms.of("  CHAIR ");

        assertThat(terms.category()).isEqualTo(ListingCategory.CHAIR);
        assertThat(terms.words()).contains("의자", "chair", "stool");
    }

    @Test
    void 묶음에_없는_검색어는_그_단어_하나로만_찾는다() {
        ListingSearchTerms terms = ListingSearchTerms.of("Very Nice Chair");

        assertThat(terms.words()).containsExactly("very nice chair");
        assertThat(terms.category()).isNull();
    }

    @Test
    void 책상과_테이블_묶음은_섞이지_않는다() {
        assertThat(ListingSearchTerms.of("책상").category()).isEqualTo(ListingCategory.DESK);
        assertThat(ListingSearchTerms.of("책상").words()).doesNotContain("테이블", "탁자");
        assertThat(ListingSearchTerms.of("기타").category()).isNull();
    }
}
