package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.common.web.FrontProperties;

@Component
public class OperatorDispatchUrlFactory {
    private final FrontProperties frontProperties;

    public OperatorDispatchUrlFactory(final FrontProperties frontProperties) {
        this.frontProperties = frontProperties;
    }

    public String create(final Long dispatchRequestId) {
        return frontProperties.baseUrl() + "/operator/dispatch-requests/" + dispatchRequestId;
    }
}
