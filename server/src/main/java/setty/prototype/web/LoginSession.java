package setty.prototype.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 프로토타입 로그인 세션을 다룬다. 세션에는 회원 식별자만 담고 휴대폰 번호·비밀번호는 담지 않는다.
 */
public final class LoginSession {
    private static final String MEMBER_ID_KEY = "PROTOTYPE_MEMBER_ID";

    private LoginSession() {
    }

    public static void start(final HttpServletRequest request, final Long memberId) {
        final HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }
        request.getSession(true).setAttribute(MEMBER_ID_KEY, memberId);
    }

    public static Long findMemberId(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        return (Long) session.getAttribute(MEMBER_ID_KEY);
    }

    public static void end(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
