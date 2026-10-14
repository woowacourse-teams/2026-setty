package setty;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundaryTest {

    // 실패하면: 다른 모듈의 내부 패키지 대신 그 모듈 바탕 패키지 타입이나 @NamedInterface가 붙은 타입을 쓰거나, 이벤트로 넘긴다.
    // 순환이면 의존 방향을 한쪽으로 정리한다.
    @Test
    void 모듈은_다른_모듈의_공개_타입만_쓰고_서로_순환하지_않는다() {
        ApplicationModules.of(SettyApplication.class).verify();
    }
}
