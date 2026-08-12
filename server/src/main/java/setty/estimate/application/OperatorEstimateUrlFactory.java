package setty.estimate.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import setty.common.web.FrontProperties;

@Component
@RequiredArgsConstructor
public class OperatorEstimateUrlFactory {
    private final FrontProperties frontProperties;

    public String create(final Long estimateRequestId) {
        return frontProperties.baseUrl() + "/operator/estimate-requests/" + estimateRequestId;
    }
}
