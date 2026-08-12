package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.common.web.FrontProperties;

@Component
public class BuyerStatusUrlFactory {
    private final FrontProperties frontProperties;

    public BuyerStatusUrlFactory(final FrontProperties frontProperties) {
        this.frontProperties = frontProperties;
    }

    /**
     * 운영자가 문자로 보내는 링크는 최종 금액 확인 화면을 곧바로 가리킨다.
     * 대기 화면 경로(`/dispatch/{buyerToken}`)로 보내면 구매자가 리다이렉트를 한 번 더 거친다.
     */
    public String create(final String buyerToken) {
        return frontProperties.baseUrl() + "/final-amount/" + buyerToken;
    }
}
