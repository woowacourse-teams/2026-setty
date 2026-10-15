package setty.global.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class ListingRequestTimingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() != DispatcherType.REQUEST
                || !"GET".equals(request.getMethod())
                || !"/api/listings".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ErrorTrackingResponse tracked = new ErrorTrackingResponse(response);
        try (ListingRequestTiming timing = ListingRequestTiming.open()) {
            try {
                chain.doFilter(request, tracked);
            } catch (IOException | ServletException | RuntimeException | Error failure) {
                // 컨테이너 오류 처리 전이므로 아직 최종 HTTP 상태나 응답 완료 시각을 알 수 없다.
                timing.log("exception", null, false, failure);
                throw failure;
            }
            if (request.isAsyncStarted()) {
                // 현재 목록 API는 동기식이다. 향후 비동기로 바뀌어도 조기 완료로 기록하지 않는다.
                timing.log("async_started", null, false, null);
            } else if (tracked.errorSent) {
                timing.log("send_error", tracked.getStatus(), false, null);
            } else {
                timing.log("completed", tracked.getStatus(), true, null);
            }
        }
    }

    private static final class ErrorTrackingResponse extends HttpServletResponseWrapper {

        private boolean errorSent;

        private ErrorTrackingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int status) throws IOException {
            errorSent = true;
            super.sendError(status);
        }

        @Override
        public void sendError(int status, String message) throws IOException {
            errorSent = true;
            super.sendError(status, message);
        }

        @Override
        public void reset() {
            super.reset();
            errorSent = false;
        }
    }
}
