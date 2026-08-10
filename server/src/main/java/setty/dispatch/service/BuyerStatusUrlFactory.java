package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.dispatch.DispatchProperties;

@Component
public class BuyerStatusUrlFactory {
    private final DispatchProperties dispatchProperties;

    public BuyerStatusUrlFactory(final DispatchProperties dispatchProperties) {
        this.dispatchProperties = dispatchProperties;
    }

    public String create(final String buyerToken) {
        return dispatchProperties.buyerStatusBaseUrl() + "/" + buyerToken;
    }
}
