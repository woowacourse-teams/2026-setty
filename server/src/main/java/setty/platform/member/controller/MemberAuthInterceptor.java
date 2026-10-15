package setty.platform.member.controller;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import setty.global.auth.InvalidTokenResponse;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;

@Component
public class MemberAuthInterceptor implements HandlerInterceptor {

    static final String LOGIN_MEMBER = "loginMember";

    private final MemberRepository memberRepository;
    private final MeterRegistry meterRegistry;

    public MemberAuthInterceptor(
            final MemberRepository memberRepository,
            final ObjectProvider<MeterRegistry> meterRegistries
    ) {
        this.memberRepository = memberRepository;
        this.meterRegistry = meterRegistries.getIfAvailable();
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        if (isPublicListingRead(request)) {
            return true;
        }

        final String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            InvalidTokenResponse.write(response);
            return false;
        }

        final String token = header.substring(7);
        final Member member = meterRegistry == null
                ? memberRepository.findByToken(token).orElse(null)
                : meterRegistry.timer("setty.auth.repository.lookup", "repository", "member",
                        "dispatcher", request.getDispatcherType().name())
                        .record(() -> memberRepository.findByToken(token).orElse(null));
        if (member == null) {
            InvalidTokenResponse.write(response);
            return false;
        }
        request.setAttribute(LOGIN_MEMBER, member);
        return true;
    }

    // 매물 목록·상세 조회만 비로그인 허용. 등록·수정·삭제(POST/PUT/DELETE)는 같은 경로라도 인증 필요.
    // 구매자 행정동이 필요한 배송비 계산·필터는 ListingService가 토큰을 직접 확인한다.
    private boolean isPublicListingRead(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return "GET".equals(request.getMethod())
                && (uri.equals("/api/listings") || uri.startsWith("/api/listings/"));
    }
}
