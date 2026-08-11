package setty.dispatch.service;

import org.springframework.stereotype.Component;
import setty.dispatch.DispatchProperties;

@Component
public class OperatorDispatchUrlFactory {
    private final DispatchProperties dispatchProperties;

    public OperatorDispatchUrlFactory(final DispatchProperties dispatchProperties) {
        this.dispatchProperties = dispatchProperties;
    }

    public String create(final Long dispatchRequestId) {
        return dispatchProperties.frontBaseUrl() + "/operator/dispatch-requests/" + dispatchRequestId;
    }
}
