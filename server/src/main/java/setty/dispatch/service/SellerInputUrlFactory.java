package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.common.web.FrontProperties;

@Component
public class SellerInputUrlFactory {
    private final FrontProperties frontProperties;

    public SellerInputUrlFactory(final FrontProperties frontProperties) {
        this.frontProperties = frontProperties;
    }

    public String create(final String sellerInputToken) {
        return frontProperties.baseUrl() + "/seller-input/" + sellerInputToken;
    }
}
