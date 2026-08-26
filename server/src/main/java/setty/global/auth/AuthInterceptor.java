package setty.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import setty.global.exception.ErrorCode;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final MemberRepository memberRepository;

    public AuthInterceptor(final MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception {
        if (isPublicListingRead(request)) {
            return true;
        }

        final String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response);
            return false;
        }

        final String token = header.substring(7);
        final Member member = memberRepository.findByToken(token).orElse(null);
        if (member == null) {
            writeError(response);
            return false;
        }

        request.setAttribute("loginMember", member);
        return true;
    }

    // 매물 목록·상세 조회만 비로그인 허용. 등록·수정·삭제(POST/PUT/DELETE)는 같은 경로라도 인증 필요.
    private boolean isPublicListingRead(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return "GET".equals(request.getMethod())
                && (uri.equals("/api/listings") || uri.startsWith("/api/listings/"));
    }

    private void writeError(final HttpServletResponse response) throws Exception {
        final ErrorCode errorCode = ErrorCode.INVALID_TOKEN;
        response.setStatus(errorCode.getStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"" + errorCode.name() + "\",\"message\":\"" + errorCode.getMessage() + "\"}"
        );
    }
}
