package setty.platform.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListingTextTest {

    @Test
    void 띄어쓰기가_달라도_같은_낱말이면_겹친다() {
        ListingText target = ListingText.of("원목 식탁 4인용", "");

        assertThat(target.similarityTo(ListingText.of("4인 원목식탁", ""))).isPositive();
        assertThat(target.similarityTo(ListingText.of("철제 수납 선반", ""))).isZero();
    }

    @Test
    void 제목이_겹치면_설명이_겹치는_것보다_높다() {
        ListingText target = ListingText.of("원목 식탁", "북유럽 스타일");

        double titleShared = target.similarityTo(ListingText.of("원목 식탁", "모던 스타일 아님"));
        double descriptionShared = target.similarityTo(ListingText.of("화이트 테이블", "북유럽 스타일"));

        assertThat(titleShared).isGreaterThan(descriptionShared);
    }

    @Test
    void 거래_표현과_가격_등급만_같으면_겹치지_않는다() {
        ListingText target = ListingText.of("급처 팝니다 S급 5만원", "직거래 가능 상태 좋아요");

        assertThat(target.similarityTo(ListingText.of("급처 팝니다 S급 5만원 원목 소파", "직거래 가능 상태 좋아요")))
                .isZero();
    }

    @Test
    void 인원수만_다른_인용은_겹치지_않는다() {
        ListingText target = ListingText.of("4인용", "");

        assertThat(target.similarityTo(ListingText.of("6인용", ""))).isZero();
        assertThat(target.similarityTo(ListingText.of("4인", ""))).isEqualTo(2.0);
    }

    @Test
    void 같은_물건의_다른_표기는_같은_낱말로_본다() {
        ListingText target = ListingText.of("원목식탁", "");

        assertThat(target.similarityTo(ListingText.of("원목 테이블", ""))).isEqualTo(2.0);
        assertThat(target.similarityTo(ListingText.of("Table", ""))).isPositive();
        assertThat(ListingText.of("옷장", "").similarityTo(ListingText.of("장롱", ""))).isEqualTo(2.0);
    }

    @Test
    void 같은_카테고리의_다른_물건은_같은_낱말로_보지_않는다() {
        ListingText target = ListingText.of("퀸 침대 프레임", "");

        assertThat(ListingText.of("침대 프레임", "").similarityTo(ListingText.of("매트리스", ""))).isZero();
        assertThat(target.similarityTo(ListingText.of("퀸 사이즈 프레임", "")))
                .isGreaterThan(target.similarityTo(ListingText.of("싱글 매트리스", "")));
        assertThat(ListingText.of("책장", "").similarityTo(ListingText.of("옷장", ""))).isZero();
    }

    @Test
    void 영어_낱말은_통째로만_같은_낱말로_본다() {
        ListingText table = ListingText.of("테이블", "");

        assertThat(table.similarityTo(ListingText.of("table", ""))).isEqualTo(2.0);
        assertThat(table.similarityTo(ListingText.of("portable", ""))).isZero();
    }

    @Test
    void 대소문자와_기호는_무시한다() {
        ListingText target = ListingText.of("IKEA 의자!", "");

        assertThat(target.similarityTo(ListingText.of("ikea/의자", ""))).isEqualTo(2.0);
    }

    @Test
    void 비어_있거나_한_글자뿐인_글은_겹침이_없다() {
        assertThat(ListingText.of("", null).similarityTo(ListingText.of("", null))).isZero();
        assertThat(ListingText.of("책 상", "").similarityTo(ListingText.of("책 상", ""))).isZero();
    }
}
