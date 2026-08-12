package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.common.web.FrontProperties;

@Component
public class BuyerStatusUrlFactory {
    private final FrontProperties frontProperties;

    public BuyerStatusUrlFactory(final FrontProperties frontProperties) {
        this.frontProperties = frontProperties;
    }

    public String create(final String buyerToken) {
        return frontProperties.baseUrl() + "/dispatch/" + buyerToken;
    }
}
