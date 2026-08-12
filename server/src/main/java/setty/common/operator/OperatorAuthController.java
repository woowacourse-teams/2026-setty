package setty.common.operator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 화면 진입 시 비밀번호가 맞는지만 확인한다.
 * 실제 검증은 {@link OperatorAuthInterceptor}가 수행하므로 이 메서드는 통과한 요청만 받는다.
 * 서버는 어떤 상태도 저장하지 않는다.
 */
@RestController
@RequestMapping("/api/operator/auth")
public class OperatorAuthController {
    @GetMapping
    public ResponseEntity<OperatorAuthResponse> verify() {
        return ResponseEntity.ok(new OperatorAuthResponse(true));
    }
}
