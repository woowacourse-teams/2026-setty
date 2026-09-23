package setty.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "requestId";
    static final String HEADER_NAME = "X-Request-Id";
    private static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        String requestId = (String) request.getAttribute(REQUEST_ATTRIBUTE);
        if (requestId == null) {
            // 외부 헤더를 채택하지 않고, 재디스패치에서만 서버가 만든 ID를 재사용한다.
            requestId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        }

        final String previousRequestId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        try {
            response.setHeader(HEADER_NAME, requestId);
            filterChain.doFilter(request, response);
        } finally {
            if (previousRequestId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousRequestId);
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(final HttpServletRequest request,
                                              final HttpServletResponse response,
                                              final FilterChain filterChain) throws ServletException, IOException {
        doFilterInternal(request, response, filterChain);
    }
}
