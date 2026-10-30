package setty.notification.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import setty.global.exception.BusinessException;
import setty.notification.keyword.domain.KeywordCategory;
import setty.platform.listing.domain.ListingCategory;

class KeywordCategoryTest {

    // 기타 매물은 카테고리 조건 없는 구독에만 알린다. 기타 구독은 아직 받지 않는다.
    @Test
    void 기타를_뺀_매물_카테고리와_같은_값을_가진다() {
        assertThat(Arrays.stream(KeywordCategory.values()).map(Enum::name))
                .containsExactly(Arrays.stream(ListingCategory.values())
                        .filter(category -> category != ListingCategory.ETC)
                        .map(Enum::name).toArray(String[]::new));
    }

    @Test
    void 비어_있으면_조건_없음이고_모르는_값은_거부한다() {
        assertThat(KeywordCategory.from(null)).isNull();
        assertThat(KeywordCategory.from(" ")).isNull();
        assertThat(KeywordCategory.from("sofa")).isEqualTo(KeywordCategory.SOFA);
        assertThatThrownBy(() -> KeywordCategory.from("TV")).isInstanceOf(BusinessException.class);
    }
}
