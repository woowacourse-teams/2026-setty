package setty.notification.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import setty.global.exception.BusinessException;
import setty.notification.keyword.domain.KeywordCategory;
import setty.platform.listing.domain.ListingCategory;

class KeywordCategoryTest {

    @Test
    void 매물_카테고리와_같은_값을_가진다() {
        assertThat(Arrays.stream(KeywordCategory.values()).map(Enum::name))
                .containsExactly(Arrays.stream(ListingCategory.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void 비어_있으면_조건_없음이고_모르는_값은_거부한다() {
        assertThat(KeywordCategory.from(null)).isNull();
        assertThat(KeywordCategory.from(" ")).isNull();
        assertThat(KeywordCategory.from("sofa")).isEqualTo(KeywordCategory.SOFA);
        assertThatThrownBy(() -> KeywordCategory.from("TV")).isInstanceOf(BusinessException.class);
    }
}
