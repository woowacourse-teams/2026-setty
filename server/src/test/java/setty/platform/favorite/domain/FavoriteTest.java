package setty.platform.favorite.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

class FavoriteTest {

    @Test
    void 회원_id가_없으면_거부된다() {
        assertThatThrownBy(() -> new Favorite(null, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 매물_id가_없으면_거부된다() {
        assertThatThrownBy(() -> new Favorite(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
