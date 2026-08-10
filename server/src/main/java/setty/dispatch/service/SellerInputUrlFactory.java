package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.dispatch.DispatchProperties;

@Component
public class SellerInputUrlFactory {
    private final DispatchProperties dispatchProperties;

    public SellerInputUrlFactory(final DispatchProperties dispatchProperties) {
        this.dispatchProperties = dispatchProperties;
    }

    public String create(final String sellerInputToken) {
        return dispatchProperties.frontBaseUrl() + "/seller-input/" + sellerInputToken;
    }
}
